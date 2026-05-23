# Stage 1: build frontend (Vite + Svelte + Tailwind)
FROM node:20-alpine AS assets
WORKDIR /assets

COPY frontend/package.json ./
RUN npm install

COPY frontend/ ./frontend/
RUN cd frontend && npm run build
# frontend/dist/ now has index.html + assets/ (hashed JS/CSS bundles + bundled fonts)

# Stage 2: Python runtime
FROM python:3.12-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy Python source
COPY . .

# Overwrite static/ with the compiled frontend
COPY --from=assets /assets/frontend/dist/ ./static/

EXPOSE 8000

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
