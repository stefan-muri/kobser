# Stage 1: build frontend (Vite + Svelte + Tailwind)
FROM node:20-alpine AS assets
WORKDIR /assets

COPY frontend/package.json ./
RUN npm install

COPY frontend/ ./frontend/
RUN cd frontend && npm run build

# Stage 2: Python runtime
FROM python:3.12-alpine

# ffmpeg from Alpine is statically-linked and much leaner than the Debian version.
# gcc + musl-dev are needed to compile uvloop (uvicorn[standard]) and psutil;
# they're removed in the same layer so they don't bloat the final image.
RUN apk add --no-cache ffmpeg \
    && apk add --no-cache --virtual .build-deps gcc musl-dev linux-headers \
    && pip install --no-cache-dir uvicorn[standard] psutil \
    && apk del .build-deps

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .
COPY --from=assets /assets/frontend/dist/ ./frontend/dist/

EXPOSE 8000

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
