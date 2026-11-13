#!/usr/bin/env python3
"""Preparación y fallos acotados exclusivamente a la base desechable CU10."""
import json, os, subprocess, sys, uuid
PASSWORD='Cu11ExternalPassword123!'
def sql(query):
    if os.environ.get('CU10_ISOLATED') != 'true': raise RuntimeError('Falta aislamiento CU10')
    command=['bash','scripts/cu10-compose.sh','exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --default-character-set=utf8mb4 -N -B -u "$MYSQL_USER" "$MYSQL_DATABASE"']
    check=subprocess.run(command,input='SELECT DATABASE();',text=True,capture_output=True,check=True,timeout=30)
    if check.stdout.strip()!='cu10_e2e': raise RuntimeError('Base ajena')
    r=subprocess.run(command,input=query,text=True,capture_output=True,timeout=30)
    if r.returncode: raise RuntimeError(r.stderr)
    return r.stdout.strip()
def create():
    token=uuid.uuid4().hex
    accounts={}
    for role in ('buyer','seller','stranger','support'):
        email=f'cu10-{role}-{token}@example.test'
        r='VENDEDOR' if role=='seller' else 'SOPORTE' if role=='support' else 'COMPRADOR'
        accounts[role]=dict(email=email,id=int(sql(f"INSERT INTO user_accounts(email,password_hash,status,email_verified_at) VALUES ('{email}','$2y$12$y0EytYkTNDIEmpnOonT1sONIUlfKJkSrna6b1z/Y6h6xZoH8l7oP2','ACTIVA',NOW(6)); SET @a=LAST_INSERT_ID(); INSERT INTO user_account_roles(account_id,role) VALUES (@a,'{r}'); SELECT @a;")))
    buyer=accounts['buyer']['id']; seller=accounts['seller']['id']
    data=json.loads(sql(f"""START TRANSACTION;
INSERT INTO stores(name,owner_account_id) VALUES ('CU10 {token}',{seller}); SET @s=LAST_INSERT_ID();
INSERT INTO products(name,price,stock,category,active,store_id) VALUES ('CU10 producto {token}',12000,20,'Hogar',TRUE,@s); SET @p=LAST_INSERT_ID();
INSERT INTO products(name,price,stock,category,active,store_id) VALUES ('CU10 segundo {token}',12000,20,'Hogar',TRUE,@s); SET @q=LAST_INSERT_ID();
INSERT INTO addresses(account_id,recipient_name,street,city,department,phone) VALUES ({buyer},'CU10','Calle 1','Bogotá','Bogotá','3001234567'); SET @a=LAST_INSERT_ID();
INSERT INTO orders(account_id,status,total,address_id,shipping_method,transaction_id,created_at,store_id,payment_status,delivery_recipient_name,delivery_street,delivery_city,delivery_department,delivery_phone,discount_amount,shipping_cost,seller_name_snapshot)
VALUES ({buyer},'CONFIRMED',24000,@a,'STANDARD','CU10-{token}',NOW(),@s,'APPROVED','CU10','Calle 1','Bogotá','Bogotá','3001234567',0,0,'CU10'); SET @o=LAST_INSERT_ID();
INSERT INTO order_items(order_id,product_id,product_name,quantity,unit_price,subtotal,unit_cost) VALUES (@o,@p,'CU10 producto {token}',1,12000,12000,3000),(@o,@q,'CU10 segundo {token}',1,12000,12000,3000);
INSERT INTO orders(account_id,status,total,address_id,shipping_method,transaction_id,created_at,store_id,payment_status,delivery_recipient_name,delivery_street,delivery_city,delivery_department,delivery_phone,discount_amount,shipping_cost,seller_name_snapshot)
SELECT account_id,status,total,address_id,shipping_method,CONCAT(transaction_id,'-2'),created_at,store_id,payment_status,delivery_recipient_name,delivery_street,delivery_city,delivery_department,delivery_phone,discount_amount,shipping_cost,seller_name_snapshot FROM orders WHERE id=@o; SET @o2=LAST_INSERT_ID();
INSERT INTO order_items(order_id,product_id,product_name,quantity,unit_price,subtotal,unit_cost) SELECT @o2,product_id,product_name,quantity,unit_price,subtotal,unit_cost FROM order_items WHERE order_id=@o;
COMMIT;
SELECT JSON_OBJECT('store',@s,'product',@p,'otherProduct',@q,'order',@o,'otherOrder',@o2,'productName','CU10 producto {token}');"""))
    return dict(**accounts,**data,password=PASSWORD)
if __name__=='__main__':
    print(sql(sys.argv[2]) if len(sys.argv)>1 and sys.argv[1]=='sql' else json.dumps(create()))
