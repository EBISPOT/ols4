
import { useEffect, useState } from 'react';
import {GLTF, GLTFLoader} from 'three/examples/jsm/loaders/GLTFLoader.js'
import * as THREE from 'three';
import { useRef } from 'react';


interface RenderState {
	renderer:THREE.WebGLRenderer
}


export default function Image3D({src}:{src:string}) {

	let divRef = useRef(null);
	let [renderState,setRenderState] = useState<RenderState|null>(null);

	useEffect(() => {
		
		async function load() {
			setRenderState(create3DImage(await loadGLTF(src)));
		} 
		load()

	}, [ src ]);

	useEffect(() => {
		
		async function load() {

			if(divRef.current) {
				let div = divRef.current as HTMLDivElement
				div.innerHTML = ''

				if(renderState) {
					div.appendChild(renderState.renderer.domElement)
				}
			}

		} 

		load()

	}, [ renderState, divRef.current ]);

	return <div className="rounded-lg mx-auto object-contain" ref={divRef}></div>
}

async function loadGLTF(src:string):Promise<GLTF> {
	var loader = new GLTFLoader();
	return new Promise<GLTF>((resolve, reject) => {
		loader.load(src, function (gltf:GLTF) {
			resolve(gltf)
		}, undefined, (err) => {
			reject(err)
		});
	})
}

function create3DImage(gltf: GLTF) {

	const camera = new THREE.PerspectiveCamera(70, window.innerWidth / window.innerHeight, 0.01, 10);
	camera.position.z = 1;

	const scene = new THREE.Scene();

	gltf.scene.scale.set(2, 2, 2);
	gltf.scene.position.y = 4;
	scene.add(gltf.scene);

	const geometry = new THREE.BoxGeometry(0.2, 0.2, 0.2);
	const material = new THREE.MeshNormalMaterial();

	const mesh = new THREE.Mesh(geometry, material);
	scene.add(mesh);

	const renderer = new THREE.WebGLRenderer({ antialias: true });
	renderer.setSize(window.innerWidth, window.innerHeight);
	renderer.setAnimationLoop(animation);

	return {
		renderer
	}

	// animation

	function animation(time) {

		mesh.rotation.x = time / 2000;
		mesh.rotation.y = time / 1000;

		renderer.render(scene, camera);

	}

}



