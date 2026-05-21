import tailwindcss from '@tailwindcss/vite';
import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vite';
import { VitePWA } from 'vite-plugin-pwa';
import path from 'path';
import fs from 'fs';

function getScalaEngineAlias() {
	const targetPath = path.resolve(__dirname, '../js/target');
	if (fs.existsSync(targetPath)) {
		const scalaDir = fs.readdirSync(targetPath).find((dir) => dir.startsWith('scala-'));
		if (scalaDir) {
			const isDev = process.env.NODE_ENV === 'development';
			const buildSuffix = isDev ? 'fastopt' : 'opt';
			return path.resolve(targetPath, scalaDir, `dicechess-engine-scala-${buildSuffix}/main.js`);
		}
	}
	return path.resolve(__dirname, '../js/target/scala-3.8.3/dicechess-engine-scala-opt/main.js'); // fallback
}

export default defineConfig({
	plugins: [
		tailwindcss(),
		sveltekit(),
		VitePWA({
			registerType: 'autoUpdate',
			manifest: {
				name: 'Dice Chess Bot',
				short_name: 'DiceChess',
				theme_color: '#0f172a',
				background_color: '#0f172a',
				display: 'standalone',
				icons: [
					{
						src: 'pwa-192x192.png',
						sizes: '192x192',
						type: 'image/png'
					},
					{
						src: 'pwa-512x512.png',
						sizes: '512x512',
						type: 'image/png'
					}
				]
			}
		})
	],
	resolve: {
		alias: {
			'dicechess-engine': getScalaEngineAlias()
		}
	}
});
