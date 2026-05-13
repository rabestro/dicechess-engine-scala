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
					label: 'Architecture & Plan',
					items: [
						{ label: 'Domain Modeling', slug: 'architecture/domain-modeling' },
						{
							label: 'Move Generation',
							items: [{ autogenerate: { directory: 'architecture/move-generation' } }],
						},
						{ label: 'Roadmap & Milestones', slug: 'architecture/milestones' },
					],
				},
				{
					label: 'Infrastructure',
					items: [
						{ label: 'Oracle Cloud Hosting', slug: 'infrastructure/oracle-cloud' },
					],
				},
				{
					label: 'Developer Rules',
					items: [
						{ label: 'Agent Workflows', slug: 'guidelines/agent-workflows' },
					],
				},
			],
		}),
	],
});
