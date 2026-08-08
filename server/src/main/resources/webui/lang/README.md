# WebUI translations

The WebUI is translated with one JSON file per language inside this folder:
`<code>.json` (e.g. `en.json`, `es.json`). The files are served as static
resources, so any language you drop in here becomes available in the UI.

## How to add a language

1. Copy `en.json` to `<code>.json`, using the ISO 639-1 language code
   (e.g. `fr`, `de`, `pt`, `ja`...).
2. Translate the **values**; keep the **keys** and the `{0}`, `{1}`... placeholders
   unchanged (the UI substitutes them at runtime).
3. Register it in the language selector in `../index.html`:
   ```html
   <select id="langSelect" onchange="setLang(this.value)" title="Language">
     <option value="en">English</option>
     <option value="es">Español</option>
     <option value="fr">Français</option>
   </select>
   ```
4. Rebuild the server and the new language is available.

## Notes

- Keys starting with `data-i18n` in the HTML use `textContent`; the `data-i18n-html`
  ones use `innerHTML` (for strings that include markup such as `<b>` or `<code>`).
- If a key is missing in a language file, the UI falls back to showing the key itself,
  so it's easy to spot gaps while translating.
- The user's choice is remembered in `localStorage` (`miwayomi.lang`) and defaults to `en`.
