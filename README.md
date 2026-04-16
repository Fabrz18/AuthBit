# 🛡️ AuthBit

**AuthBit** es un plugin de autenticación ligero, seguro y altamente personalizable para servidores de Minecraft (Spigot/Paper). Diseñado para integrarse perfectamente en redes proxy (BungeeCord/Velocity), ofrece un sistema de login fluido, soporte para cuentas premium y protección avanzada de datos.

Parte del ecosistema de herramientas de **BitRaid**.

---

## ✨ Características Principales

* **Autenticación Segura:** Sistema robusto de `/login` y `/register` con validación de base de datos.
* **Integración Premium (FastLogin):** Los usuarios premium pueden usar `/premium` para activar el inicio de sesión automático. Se integra de forma nativa con la API de FastLogin.
* **Protección de Consola (Log4j2):** Intercepta y bloquea silenciosamente las contraseñas escritas en el chat antes de que el servidor las registre en el archivo `latest.log` o en la consola.
* **Caché de Sesiones Inteligente:** Si un jugador se desconecta, su sesión se guarda en memoria por 10 minutos. Si vuelve a entrar con la misma IP dentro de ese lapso, no necesitará loguearse de nuevo.
* **Redirección a Proxy:** Al iniciar sesión exitosamente, el jugador es enviado automáticamente al servidor `lobby` configurado en BungeeCord o Velocity.
* **100% Personalizable:** Separación limpia de configuraciones (`config.yml` para base de datos/ajustes técnicos y `messages.yml` para todos los textos y colores del plugin).

---

## 📜 Comandos y Permisos

| Comando | Descripción | Permiso |
| :--- | :--- | :--- |
| `/register <pass> <pass>` | Registra una cuenta nueva. | Ninguno |
| `/login <pass>` | Inicia sesión en una cuenta existente. | Ninguno |
| `/premium` | Activa el modo premium para inicio de sesión automático. | Ninguno |
| `/unregister <nick>` | Elimina el registro y la caché de un usuario. | `OP` (Operador) |

---

## ⚙️ Configuración

El plugin generará automáticamente dos archivos en la carpeta `plugins/AuthBit/`:

### `messages.yml`
Aquí puedes editar todos los mensajes que reciben los jugadores. Soporta códigos de color nativos usando `&`.

```yaml
prefix: "&8[&bAuthBit&8] "
registro-exitoso: "&a¡Registro exitoso!"
login-exitoso: "&a¡Sesión iniciada! Redirigiendo al Lobby..."
# ... (y más opciones)
