# Volumen+

App de Android para controlar el volumen cuando los botones físicos están rotos.
Sin anuncios, sin internet, sin dependencias externas (solo APIs de Android). Android 8.0 o superior.

## Qué incluye

| Función | Para qué sirve | Permiso |
|---|---|---|
| **Botón flotante ＋/－** | Siempre visible sobre cualquier app. Toque = un paso, mantener = seguido, arrastrar = mover (se pega al borde y se vuelve transparente si no lo usas). | Mostrar sobre otras apps |
| **Mosaicos de Ajustes rápidos** | «Subir volumen» y «Bajar volumen» en el panel que se abre al deslizar desde arriba. Funcionan con la pantalla bloqueada. | Ninguno |
| **Controles en la notificación** | ＋ / － desde la barra de notificaciones. | Notificaciones (opcional) |
| **Pantalla principal** | Barra deslizante y botones para Multimedia, Tono y Alarma, silenciar y abrir el panel del sistema. | Ninguno |
| **Arranque automático** | El botón flotante vuelve solo al reiniciar el celular. | — |

## Obtener el APK (sin instalar Android Studio)

1. Crea un repositorio nuevo en GitHub (puede ser privado), p. ej. `VolumenPlus`.
2. Sube **todo el contenido** de esta carpeta (incluida la carpeta oculta `.github`).
   - Desde la PC: `git init && git add . && git commit -m "Volumen+" && git branch -M main && git remote add origin <url> && git push -u origin main`
3. GitHub Actions compila solo (pestaña **Actions**, unos 3–5 min).
4. En el celular, abre el repo → **Releases** → descarga `VolumenPlus.apk` → ábrelo.
   - Si Android pregunta, permite «Instalar apps desconocidas» para tu navegador.
   - Si Play Protect avisa «app desconocida», toca **Instalar de todas formas** (es tu propia app).

Alternativa: abrir la carpeta en Android Studio y darle ▶ Run con el celular conectado.

## Primer uso

1. Abre Volumen+ → activa **Mostrar botón flotante** → concede «Mostrar sobre otras apps» → vuelve.
2. Toca **Agregar mosaicos** (Android 13+) o agrégalos a mano desde el lápiz ✏️ del panel rápido.
3. Recomendado: en Ajustes → Batería → Volumen+, elige «Sin restricciones», para que el sistema no cierre el botón flotante (sobre todo en Xiaomi, Huawei, Oppo y Samsung).

## Estructura

```
app/src/main/java/com/isai/volumen/
├── VolumeController.kt      # lógica única de volumen (AudioManager)
├── FloatingVolumeService.kt # botón flotante + notificación (servicio en primer plano)
├── VolumeTiles.kt           # mosaicos de Ajustes rápidos (TileService)
├── MainActivity.kt          # pantalla principal
├── BootReceiver.kt          # reactivar tras reinicio
└── Prefs.kt                 # preferencias (posición, flujo elegido)
```

Decisión de diseño: **no** se usa un Servicio de Accesibilidad. Desde Android 13, Android bloquea ese permiso en
apps instaladas fuera de Play Store («ajuste restringido»), así que la superposición y los mosaicos funcionan mejor aquí.
