# ADR-0001: Recortar el drive lógico en lugar de materializarlo

- **Estado**: Aceptado
- **Fecha**: 2026-08-22
- **Contexto**: revisión arquitectónica del proyecto (candidato 2, "el drive lógico: deep o muerto")

## Contexto

El README prometía "logical drive features" (virtual paths, archive state, trash) y la API exponía cuatro endpoints para ello (restore, move, archive, list trash), pero la implementación nunca existió: los cuatro métodos fallaban siempre con "Logical … is disabled", el indexador de metadatos que debía sostenerlos devolvía vacío en 8 de sus 11 métodos (nunca persistió nada), los uploads descartaban los parámetros lógicos, y los defaults vivían duplicados entre el stub y el DTO de respuesta. Cada revisión de arquitectura redescubría que la feature era aspiracional.

## Decisión

Recortar. Se eliminan los cuatro métodos y sus endpoints, el indexador stub con su modelo y su cache huérfana, y los DTOs que solo servían a operaciones imposibles. La respuesta de subida conserva sus campos lógicos descriptivos (checksum, virtual path, tags). La decisión se toma por defectecto de YAGNI: ninguna demanda validada sostiene construir la feature.

## Alternativas consideradas

- **Materializar el drive lógico**: construir persistencia real (papelera, versiones, checksums, rutas virtuales). Rechazado por ser funcionalidad nueva sin demanda validada; el costo de construcción y mantenimiento no se justifica mientras ningún usuario la haya pedido. Si el roadmap la pide, será diseño nuevo — no resucitar este stub.

## Consecuencias

- La API deja de ofrecer operaciones que siempre fallan; la surface restante es ejecutable de punta a punta.
- El README describe el producto real.
- Si algún día se quiere el drive lógico, partir de este ADR: diseñar con persistencia real y una interface que solo declare lo implementado.
- Los parámetros lógicos de subida (virtualPath, tags, origin, archived) siguen aceptándose y reflejándose en la respuesta como metadatos descriptivos, sin efecto en el almacenamiento.
