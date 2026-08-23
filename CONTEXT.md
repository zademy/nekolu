# CONTEXT.md — Glosario del workspace

Términos del dominio de Nekolu, tal como los usa el código y la documentación. Los nombres nuevos deberían salir de aquí; si un concepto falta, añadirlo cuando se resuelva.

## Workspace

**Workspace** — Nekolu mismo: Telegram usado como disco personal de archivos.

**Saved Messages** — el chat propio del usuario; el almacenamiento por defecto del workspace.

**Folder (carpeta)** — un canal privado de Telegram que respalda una carpeta del workspace. Crear una carpeta es crear un canal; no hay entidad carpeta propia.

**Folder channel** — sinónimo técnico de folder (el respaldo en Telegram).

## Archivos

**File message** — un mensaje de Telegram que porta un archivo (photo, video, audio, document, voice, video_note). La unidad que el workspace lista, busca y muestra.

**File ID / Message ID / Chat ID** — identificadores TDLib tal como cruzan el sistema. El seam los habla en `long`.

**Downloaded (descargado)** — existe una copia local completa del archivo EN DISCO. Semántica única decidida por el módulo Telegram; nadie más la recalcula.

**File type** — taxonomía del workspace: `photo`, `video`, `audio`, `document`, `voice`, `video_note` (más `all` para búsquedas). Fuente única en el módulo Telegram.

## Subidas

**Staged file** — archivo local temporal que TDLib usa como fuente de una subida. Vive en el área de staging (`tdlib/upload-staging`); su ciclo completo (materializar → publicar → limpiar al confirmar → purgar vencidos) pertenece al módulo de staging y al seam de Telegram.

**Upload staging area** — el módulo dueño de ese ciclo.

## Descargas

**Polling** — el contrato único de progreso de descarga: iniciar la descarga (respuesta inmediata con estado inicial) y sondear el estado del archivo. No hay mecanismos de push.

**Download state** — tamaño, progreso, ruta local y la semántica de `downloaded` de un archivo, expuestos por el seam.

## Errores

Los modos de error del workspace viajan por tipo (paquete `exception`): **Unauthorized** (401, sesión sin autenticar), **NotInitialized** (503, módulo sin cliente), **Telegram operation error** (502, rechazo upstream con código TDLib), **Not found** (404, recurso inexistente). El manejador global es el único traductor de errores de transporte a HTTP; los resultados de negocio fallidos (subida rechazada, borrado fallido) se mapean a su DTO de resultado en el controller, capturando únicamente `TelegramOperationException` — todo lo demás sube.

## Términos retirados

**Logical drive / virtual path / archive state / logical trash** — retirados por ADR-0001: nunca se implementaron. No reintroducir sin leer el ADR.
