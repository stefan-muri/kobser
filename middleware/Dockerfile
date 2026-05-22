# Stage 1: build static assets (Tailwind CSS + Phosphor Icons fonts)
FROM node:20-alpine AS assets
WORKDIR /assets

COPY package.json ./
RUN npm install

COPY tailwind.config.js tailwind.input.css ./
COPY static/ static/

# Compile Tailwind from source scanning all HTML/JS
RUN npx tailwindcss -i tailwind.input.css -o static/dist/tailwind.css --minify

# Copy Phosphor Icons CSS + fonts into the vendor directory
RUN mkdir -p static/vendor/phosphor && \
    cp -r node_modules/@phosphor-icons/web/src/. static/vendor/phosphor/

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

# Overwrite static/ with the compiled version from the asset stage
COPY --from=assets /assets/static/ ./static/

EXPOSE 8000

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
