#!/usr/bin/env python3
"""Fixtures exclusivas CU-09; no realizan compras ni pagos reales."""
import json, os, subprocess, sys, uuid

def sql(query):
    if os.environ.get('CU09_ISOLATED') != 'true':
        raise RuntimeError('Se requiere aislamiento explícito CU09')
    command = ['bash', 'scripts/cu09-compose.sh', 'exec', '-T', 'mysql', 'sh', '-c',
        'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --default-character-set=utf8mb4 -N -B -u "$MYSQL_USER" "$MYSQL_DATABASE"']
    check = subprocess.run(command, input='SELECT DATABASE();', text=True, capture_output=True, check=True, timeout=30)
    if check.stdout.strip() != 'cu09_e2e': raise RuntimeError('Base ajena a CU09')
    return subprocess.run(command, input=query, text=True, capture_output=True, check=True, timeout=30).stdout.strip()

def account():
    email='cu09-'+uuid.uuid4().hex+'@example.test'
    sql(f"""START TRANSACTION;
    INSERT INTO user_accounts(email,password_hash,status,email_verified_at) VALUES ('{email}','$2y$12$1b8lQufQ.HVnswuPxAClJ.mZciYOAGr8F2XGKjzndmAJQ7t94AK7m','ACTIVA',NOW(6));
    SET @a=LAST_INSERT_ID();
    INSERT INTO user_account_roles(account_id,role) VALUES (@a,'COMPRADOR'),(@a,'VENDEDOR');
    INSERT INTO account_profiles(account_id,full_name,birth_date,phone) VALUES (@a,'Ana Perfil','2000-01-01','3001234567');
    COMMIT;""")
    return email

if __name__ == '__main__':
    if len(sys.argv)>1 and sys.argv[1]=='bloquear':
        email=sys.argv[2]
        import re
        if not re.fullmatch(r'cu09-[a-f0-9]+@example\.test',email): raise ValueError('Identidad ajena a CU09')
        print(sql(f"""SET @a=(SELECT id FROM user_accounts WHERE email='{email}');
        INSERT INTO addresses(account_id,recipient_name,street,city,department,phone) VALUES (@a,'Ana CU09','Calle CU09','Bogotá','Bogotá','3001234567'); SET @d=LAST_INSERT_ID();
        INSERT INTO orders(account_id,status,total,address_id,shipping_method,transaction_id,created_at,store_id,payment_status,
            delivery_recipient_name,delivery_street,delivery_city,delivery_department,delivery_phone,discount_amount,shipping_cost,seller_name_snapshot)
        VALUES (@a,'CONFIRMED',100,@d,'STANDARD',UUID(),NOW(),1,'APPROVED','Ana CU09','Calle CU09','Bogotá','Bogotá','3001234567',0,0,'Tienda');"""))
    else: print(sql(sys.argv[2]) if len(sys.argv)>1 and sys.argv[1]=='sql' else account())
