/**
 * Construye un .xlsx mínimo (zip sin comprimir, una sola hoja, celdas de texto) sin depender de ninguna librería de
 * Excel: alcanza para subir los archivos que valida el panel de Inventario (CU-15). Misma idea que
 * MinimalXlsx.java del repositorio de pruebas de integración por HTTP.
 */

function crc32(data: Buffer): number {
  const table = crc32Table();
  let crc = 0xffffffff;
  for (const byte of data) {
    crc = (crc >>> 8) ^ table[(crc ^ byte) & 0xff];
  }
  return (crc ^ 0xffffffff) >>> 0;
}

let cachedTable: number[] | null = null;
function crc32Table(): number[] {
  if (cachedTable) return cachedTable;
  const table: number[] = [];
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) {
      c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    }
    table[n] = c;
  }
  cachedTable = table;
  return table;
}

/** Un .zip sin comprimir (método STORE): más simple que implementar deflate y perfectamente válido. */
function zipStore(files: Array<[name: string, content: string]>): Buffer {
  const localParts: Buffer[] = [];
  const centralParts: Buffer[] = [];
  let offset = 0;

  for (const [name, content] of files) {
    const data = Buffer.from(content, 'utf-8');
    const nameBuf = Buffer.from(name, 'utf-8');
    const crc = crc32(data);

    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0);
    local.writeUInt16LE(20, 4);
    local.writeUInt32LE(crc, 14);
    local.writeUInt32LE(data.length, 18);
    local.writeUInt32LE(data.length, 22);
    local.writeUInt16LE(nameBuf.length, 26);
    localParts.push(local, nameBuf, data);

    const central = Buffer.alloc(46);
    central.writeUInt32LE(0x02014b50, 0);
    central.writeUInt16LE(20, 4);
    central.writeUInt16LE(20, 6);
    central.writeUInt32LE(crc, 16);
    central.writeUInt32LE(data.length, 20);
    central.writeUInt32LE(data.length, 24);
    central.writeUInt16LE(nameBuf.length, 28);
    central.writeUInt32LE(offset, 42);
    centralParts.push(central, nameBuf);

    offset += local.length + nameBuf.length + data.length;
  }

  const centralBuf = Buffer.concat(centralParts);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(files.length, 8);
  end.writeUInt16LE(files.length, 10);
  end.writeUInt32LE(centralBuf.length, 12);
  end.writeUInt32LE(offset, 16);

  return Buffer.concat([...localParts, centralBuf, end]);
}

function escapeXml(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function columnLetter(index: number): string {
  return String.fromCharCode(65 + index);
}

function rowXml(rowNumber: number, values: string[]): string {
  const cells = values
    .map((value, column) =>
      value ? `<c r="${columnLetter(column)}${rowNumber}" t="inlineStr"><is><t>${escapeXml(value)}</t></is></c>` : '')
    .join('');
  return `<row r="${rowNumber}">${cells}</row>`;
}

/** La primera fila son los encabezados; cada fila de datos es una lista de textos en el mismo orden. */
export function buildXlsx(headers: string[], rows: string[][]): Buffer {
  const sheet = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
    + '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>'
    + rowXml(1, headers) + rows.map((row, index) => rowXml(index + 2, row)).join('')
    + '</sheetData></worksheet>';

  const contentTypes = '<?xml version="1.0" encoding="UTF-8"?>'
    + '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
    + '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
    + '<Default Extension="xml" ContentType="application/xml"/>'
    + '<Override PartName="/xl/workbook.xml" '
    + 'ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>'
    + '<Override PartName="/xl/worksheets/sheet1.xml" '
    + 'ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>';

  const rels = '<?xml version="1.0" encoding="UTF-8"?>'
    + '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
    + '<Relationship Id="rId1" '
    + 'Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" '
    + 'Target="xl/workbook.xml"/></Relationships>';

  const workbook = '<?xml version="1.0" encoding="UTF-8"?>'
    + '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" '
    + 'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">'
    + '<sheets><sheet name="Datos" sheetId="1" r:id="rId1"/></sheets></workbook>';

  const workbookRels = '<?xml version="1.0" encoding="UTF-8"?>'
    + '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
    + '<Relationship Id="rId1" '
    + 'Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" '
    + 'Target="worksheets/sheet1.xml"/></Relationships>';

  return zipStore([
    ['[Content_Types].xml', contentTypes],
    ['_rels/.rels', rels],
    ['xl/workbook.xml', workbook],
    ['xl/_rels/workbook.xml.rels', workbookRels],
    ['xl/worksheets/sheet1.xml', sheet],
  ]);
}

export const XLSX_MIME = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
export const PRODUCT_HEADERS =
  ['Nombre', 'Descripción', 'Precio', 'Inventario', 'Categoría', 'Marca', 'Imágenes', 'Publicar'];
export const STOCK_HEADERS = ['ID producto', 'Producto', 'Stock actual', 'Reservado', 'Tipo', 'Cantidad', 'Motivo'];
