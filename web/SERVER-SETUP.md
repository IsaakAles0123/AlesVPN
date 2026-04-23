# Лендинг alesvpn.ru на VPS (Ubuntu/Debian)

**Перед Certbot** проверь: `nslookup alesvpn.ru` возвращает IP сервера (без `NXDOMAIN`).

## 1. Подключение по SSH

```bash
ssh root@ВАШ_IP
```

(Логин/пароль или ключ — в панели Timeweb → сервер → **Доступ**.)

## 2. Nginx и каталог

```bash
sudo apt update
sudo apt install -y nginx
sudo mkdir -p /var/www/alesvpn
sudo chown -R $USER:$USER /var/www/alesvpn
```

## 3. Загрузка файлов с Windows (PowerShell **на твоём ПК**)

Из папки проекта (путь при необходимости поправь):

```powershell
cd C:\MyVPN\web
scp -r index.html assets root@ВАШ_IP:/var/www/alesvpn/
```

В `assets` входят `style.css`, `stars.js`, `favicon.svg` — весь каталог **целиком** перезаливай при обновлении лендинга.

Если `scp` ругается, используй **WinSCP** / **FileZilla (SFTP)**:
- хост: IP сервера, порт 22, пользователь `root`
- слева: `C:\MyVPN\web`, справа: `/var/www/alesvpn/`
- залей `index.html` и папку `assets` **целиком**.

Права для Nginx:

```bash
sudo chown -R www-data:www-data /var/www/alesvpn
```

## 4. Конфиг Nginx

На **сервере** (файл можно создать через `nano`):

```bash
sudo nano /etc/nginx/sites-available/alesvpn
```

Вставь содержимое из репозитория: `web/nginx-alesvpn-site.conf`, либо скопируй с ПК:

```bash
# с ПК в PowerShell, из C:\MyVPN\web
scp nginx-alesvpn-site.conf root@ВАШ_IP:/tmp/alesvpn.conf
```

На сервере:

```bash
sudo mv /tmp/alesvpn.conf /etc/nginx/sites-available/alesvpn
sudo ln -sf /etc/nginx/sites-available/alesvpn /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl reload nginx
```

Проверь в браузере: `http://alesvpn.ru` — должен грузиться твой `index.html`. Если `NXDOMAIN` в браузере — **ещё** не DNS, не Nginx.

## 5. Фаервол (если ufw)

```bash
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 51820/udp
sudo ufw status
```

## 6. HTTPS (Let’s Encrypt)

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d alesvpn.ru -d www.alesvpn.ru
```

(Email при запросе, согласие с ToS, редирект с HTTP на HTTPS — по желанию «2».)

Проверка: `https://alesvpn.ru`

## 7. Обновления файлов сайта

Повторяй `scp` или WinSCF в `/var/www/alesvpn/`, `certbot` заново **не** нужен, пока не сменил домен.

---

Ошибка Certbot: **нужен** рабочий `http://alesvpn.ru` с этого же сервера и **порт 80** с интернета. Проверь **DNS** и **ufw/облачный** фаервол (панель Timeweb, группы безопасности), чтобы **80/443** были **вход**.
