La primera regresión general encontró 4 fallos de moderación con 401
`STORE_IDENTITY_MISSING`: CU-12 ya había creado la tienda 2 para una cuenta
sintética antes de ejecutar `cu20-demo-seed.sh`. Ese script presupone que la
tienda 2 está libre. Se añadió una tienda independiente para la cuenta ficticia
`vecino.demo@example.com` dentro de `cu08_e2e`, sin cambiar pruebas ni producción.
La ejecución final de Maven se repite con este fixture corregido.
