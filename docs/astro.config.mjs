// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import mermaid from 'astro-mermaid';

// https://astro.build/config
export default defineConfig({
	site: 'https://jc.id.lv',
	base: '/dicechess-engine-scala',
	integrations: [
		mermaid(),
		starlight({
			title: 'Dice Chess Engine',
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
						{ label: 'AI Agent Workflows', slug: 'guidelines/agent-workflows' },
					],
				},
			],
		}),
	],
});
