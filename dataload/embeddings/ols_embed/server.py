#!/usr/bin/env python3

import os
import sys
from pathlib import Path
from typing import Dict, List, Optional, Tuple
import struct

from flask import Flask, request, jsonify, Response
from sentence_transformers import SentenceTransformer
import numpy as np

app = Flask(__name__)

loaded_models: Dict[str, SentenceTransformer] = {}

MODELS_DIR = os.environ.get('MODELS_DIR', '/data/models')

def load_all_models():
    models_path = Path(MODELS_DIR)
    if not models_path.exists():
        print(f"Warning: Models directory not found: {MODELS_DIR}", file=sys.stderr)
        return
    
    for item in models_path.iterdir():
        if item.is_dir() and not item.name.startswith('.'):
            model_name = item.name
            print(f"Loading model: {model_name} from {item}", file=sys.stderr)
            loaded_models[model_name] = SentenceTransformer(
                str(item),
                trust_remote_code=True,
                device='cuda'
            )
            print(f"Loaded model: {model_name}", file=sys.stderr)

@app.route('/spot/embed/models', methods=['GET'])
def list_models():
    models_path = Path(MODELS_DIR)
    all_models = []
    
    if models_path.exists():
        for item in models_path.iterdir():
            if item.is_dir() and not item.name.startswith('.'):
                all_models.append(item.name)
    
    all_loaded = list(loaded_models.keys())
    
    return jsonify({
        'models': sorted(all_models),
        'loaded_models': sorted(all_loaded)
    })

@app.route('/spot/embed', methods=['POST'])
def embed():
    data = request.get_json()
    
    if not data:
        return jsonify({'error': 'Request body must be JSON'}), 400
    
    model_name = data.get('model')
    text = data.get('text')
    
    if not model_name:
        return jsonify({'error': 'model parameter is required'}), 400
    
    if not text:
        return jsonify({'error': 'text parameter is required'}), 400
    
    if model_name not in loaded_models:
        return jsonify({'error': f'Model not found or failed to load: {model_name}'}), 404
    
    embedder = loaded_models[model_name]
    
    try:
        if not isinstance(text, list):
            return jsonify({'error': 'text must be a list of strings'}), 400
        
        embeddings = embedder.encode(
            text,
            normalize_embeddings=True,
            show_progress_bar=False,
            device='cuda',
            convert_to_numpy=True
        )
        
        embeddings_array = np.asarray(embeddings, dtype=np.float32)
        
        num_vectors, dimension = embeddings_array.shape
        
        binary_data = embeddings_array.tobytes()
        
        response = Response(binary_data, mimetype='application/octet-stream')
        response.headers['x-embedding-dim'] = str(dimension)
        
        return response
        
    except Exception as e:
        print(f"Error embedding text: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc(file=sys.stderr)
        return jsonify({'error': f'Error embedding text: {str(e)}'}), 500

print(f"Models directory: {MODELS_DIR}", file=sys.stderr)
models_path = Path(MODELS_DIR)
if models_path.exists():
    available = [item.name for item in models_path.iterdir() 
                if item.is_dir() and not item.name.startswith('.')]
    print(f"Available models: {sorted(available)}", file=sys.stderr)
else:
    print("No models directory found", file=sys.stderr)
print("", file=sys.stderr)

print("Loading all models...", file=sys.stderr)
load_all_models()
print(f"Loaded {len(loaded_models)} models: {list(loaded_models.keys())}", file=sys.stderr)
print("", file=sys.stderr)
