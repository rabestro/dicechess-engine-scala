// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import mermaid from 'astro-mermaid';
import { starlightKatex } from 'starlight-katex';

// https://astro.build/config
export default defineConfig({
	site: 'https://jc.id.lv',
	base: '/dicechess-engine-scala',
	integrations: [
		mermaid(),
		starlight({
			title: 'Dice Chess Engine',
			plugins: [starlightKatex()],
			social: [{ icon: 'github', label: 'GitHub', href: 'https://github.com/rabestro/dicechess-engine-scala' }],
			sidebar: [
				{
					label: 'Getting Started',
					items: [
						{ label: 'Roadmap & Milestones', slug: 'architecture/milestones' },
					],
				},
				{
					label: 'Core Architecture',
					items: [
						{ label: 'Glossary', slug: 'architecture/glossary' },
						{ label: 'Domain Modeling', slug: 'architecture/domain-modeling' },
						{ label: 'Dice Chess FEN (DFEN)', slug: 'architecture/dice-chess-fen' },
						{ label: 'Primitive Best-Move Search', slug: 'architecture/primitive-search' },
						{ label: 'Search Roadmap & Evaluation', slug: 'architecture/search-roadmap' },
						{ label: 'King Capture Test Cases', slug: 'architecture/search/king-capture-probability-test-cases' },
						{
							label: 'Move Generation',
							collapsed: true,
							items: [{ autogenerate: { directory: 'architecture/move-generation' } }],
						},
					],
				},
				{
					label: 'Engineering Quality',
					items: [
						{ label: 'Testing Strategy & DSL', slug: 'architecture/testing' },
						{ label: 'CI/CD & Releases', slug: 'architecture/releases' },
						{ label: 'Automated Code Reviews', slug: 'architecture/code-reviews' },
						{ label: 'Security Policy', slug: 'architecture/security' },
					],
				},
				{
					label: 'Infrastructure & Ops',
					items: [
						{ label: 'Oracle Cloud Hosting', slug: 'infrastructure/oracle-cloud' },
					],
				},
				{
					label: 'Developer Experience',
					items: [
						{ label: 'JavaScript API Reference', slug: 'architecture/javascript-api' },
						{ label: 'AI Agent Workflows', slug: 'guidelines/agent-workflows' },
						{ label: 'NPM Packaging & Local Integration', slug: 'guidelines/npm-packaging' },
					],
				},
			],
		}),
	],
});
