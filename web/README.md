# Лендинг AlesVPN

Статическая вёрстка: `index.html`, `assets/style.css`, `assets/stars.js`.

- **Просмотр локально:** из папки `web` выполнить `npx --yes serve .` или `python -m http.server 8080`, открыть в браузере.
- **Ссылки:** внизу `index.html` блок `<div id="config" data-tg="..." data-apk="...">`. Кнопки оплаты на лендинге ведут на `/pay/#android` и `/pay/#ios`. URL кассы в Android `ales_purchase_url` — на `/pay/` или `/pay/#android`.
- **Хостинг:** GitHub Pages, Netlify, VPS — достаточно залить содержимое `web/`.
