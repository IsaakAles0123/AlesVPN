# Лендинг AlesVPN

Статическая вёрстка: `index.html`, `assets/style.css`, `assets/stars.js`.

- **Просмотр локально:** из папки `web` выполнить `npx --yes serve .` или `python -m http.server 8080`, открыть в браузере.
- **Ссылки:** внизу `index.html` блок `<div id="config" data-pay="..." data-tg="..." data-mail="...">` — задай свои URL и почту. Тот же URL положи в Android `ales_purchase_url` в `strings.xml`, чтобы кнопка Get Plus вела на сайт.
- **Хостинг:** GitHub Pages, Netlify, VPS — достаточно залить содержимое `web/`.
