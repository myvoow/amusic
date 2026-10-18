FROM python:3.12-slim
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg && rm -rf /var/lib/apt/lists/*
RUN pip install --no-cache-dir essentia==2.1b6.dev1389 numpy
COPY analyze.py /app/analyze.py
CMD ["python", "/app/analyze.py"]
