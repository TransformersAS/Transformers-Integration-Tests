#!/usr/bin/env python3
"""SQL fixtures, never a UI purchase. Restricted to the disposable CU11 database."""
import argparse, datetime, json, os, subprocess, uuid
from urllib.parse import urlparse
PASSWORD = 'Cu11ExternalPassword123!'
PASSWORD_HASH = '$2y$12$y0EytYkTNDIEmpnOonT1sONIUlfKJkSrna6b1z/Y6h6xZoH8l7oP2'

def sql(query):
    if os.environ.get('CU11_ISOLATED') != 'true' or urlparse(os.environ.get('BACKEND_BASE_URL', '')).hostname not in ('127.0.0.1', 'localhost'):
        raise RuntimeError('CU11 requires explicit isolation and a loopback API')
    command = ['bash', 'scripts/cu11-compose.sh', 'exec', '-T', 'mysql', 'sh', '-c',
               'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --default-character-set=utf8mb4 -N -B -u "$MYSQL_USER" "$MYSQL_DATABASE"']
    check = subprocess.run(command, input='SELECT DATABASE();', text=True, capture_output=True, timeout=30, check=True)
    if check.stdout.strip() != 'cu11_e2e': raise RuntimeError('Refusing a database outside CU11')
    result = subprocess.run(command, input=query, text=True, capture_output=True, timeout=30)
    if result.returncode: raise RuntimeError('Disposable CU11 SQL failed: '+result.stderr)
    return result.stdout.strip()

def quoted(value): return "'" + value.replace("'", "''") + "'"

def create(args):
    email = args.email or 'cu11-'+uuid.uuid4().hex+'@example.test'
    if not email.startswith('cu11-') or not email.endswith('@example.test'): raise ValueError('Only CU11 fixture identities are allowed')
    datetime.datetime.fromisoformat(args.created_at)
    transaction = ('MP-'+str(uuid.uuid4().int % 10**18)) if args.mp else 'CU11-FIXTURE-'+uuid.uuid4().hex
    shipment = """INSERT INTO shipments(order_id,provider_shipment_id,tracking_code,idempotency_key,status,created_at)
        VALUES (@o,CONCAT('CU11-',@o),CONCAT('CU11-',@o),CONCAT('CU11-',@o),'CREATED',NOW(6));""" if args.shipment else ''
    query = f"""
    START TRANSACTION;
    INSERT INTO user_accounts(email,password_hash,status,email_verified_at)
    VALUES ({quoted(email)},{quoted(PASSWORD_HASH)},'ACTIVA',NOW(6)) ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id);
    SET @a=LAST_INSERT_ID();
    INSERT IGNORE INTO user_account_roles(account_id,role) VALUES (@a,'COMPRADOR'),(@a,'VENDEDOR');
    INSERT INTO addresses(account_id,recipient_name,street,city,department,phone)
    VALUES (@a,'Ana CU11','Calle CU11 123','Bogotá','Bogotá','3001234567'); SET @address=LAST_INSERT_ID();
    INSERT INTO products(name,price,stock,category,active,store_id) VALUES ('Camiseta fixture CU11',12000,8,'Hogar',TRUE,1);
    SET @p=LAST_INSERT_ID();
    INSERT INTO orders(account_id,status,total,address_id,shipping_method,transaction_id,created_at,store_id,payment_status,
        delivery_recipient_name,delivery_street,delivery_city,delivery_department,delivery_phone,
        discount_amount,shipping_cost,seller_name_snapshot)
    VALUES (@a,{quoted(args.status)},32000,@address,'STANDARD',{quoted(transaction)},{quoted(args.created_at)},1,'APPROVED',
        'Ana CU11','Calle CU11 123','Bogotá','Bogotá','3001234567',2000,10000,'Tienda principal'); SET @o=LAST_INSERT_ID();
    INSERT INTO order_items(order_id,product_id,product_name,quantity,unit_price,subtotal,unit_cost)
    VALUES (@o,@p,'Camiseta fixture CU11',2,12000,24000,3000);
    INSERT INTO order_status_history(order_id,from_status,to_status,actor_type,reason,correlation_id,created_at)
    VALUES (@o,NULL,{quoted(args.status)},'SYSTEM','Fixture SQL CU11, no compra real','cu11-fixture',{quoted(args.created_at)});
    {shipment}
    COMMIT;
    SELECT JSON_OBJECT('email',{quoted(email)},'accountId',@a,'addressId',@address,'productId',@p,'orderId',@o,
        'transactionId',{quoted(transaction)},'total',32000,'productName','Camiseta fixture CU11','status',{quoted(args.status)});
    """
    return json.loads(sql(query))

if __name__ == '__main__':
    p=argparse.ArgumentParser();p.add_argument('--sql',help='Observation or fault fixture, restricted to CU11')
    p.add_argument('--email');p.add_argument('--status',default='CONFIRMED',choices=['CONFIRMED','IN_PREPARATION','READY_FOR_DISPATCH','PICKED_UP','IN_TRANSIT','DELIVERED','DELIVERY_EXCEPTION','DELIVERY_ATTEMPT_FAILED','RETURNED_TO_SELLER','CANCELLED','CANCELLATION_REQUESTED'])
    p.add_argument('--created-at',default='2026-01-01T12:00:00');p.add_argument('--mp',action='store_true');p.add_argument('--shipment',action='store_true')
    args=p.parse_args();print(sql(args.sql) if args.sql is not None else json.dumps(create(args),ensure_ascii=False))
