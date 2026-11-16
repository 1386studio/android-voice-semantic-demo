#!/usr/bin/env python3
"""Deprecated compatibility entrypoint; Vosk is no longer downloaded."""
from fetch_sensevoice import extract
from fetch_sherpa_onnx import fetch as fetch_aar
from fetch_silero_vad import fetch as fetch_vad

if __name__ == '__main__':
    fetch_aar()
    extract()
    fetch_vad()
