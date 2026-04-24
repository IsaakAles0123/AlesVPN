# Build from repository root: docker compose build
FROM python:3.12-slim-bookworm

WORKDIR /app

COPY pay_api/requirements.txt /tmp/req/pay.txt
COPY bots/telegram/requirements.txt /tmp/req/bot.txt
RUN pip install --no-cache-dir -r /tmp/req/pay.txt -r /tmp/req/bot.txt

ENV PYTHONPATH=/app/bots/telegram
EXPOSE 8008

# Source is bind-mounted in docker-compose.yml; install deps in image only.
