# VICKEY: Mining Conditional Keys on Knowledge Bases

## Introduction
A conditional key is a key constraint that is valid in only a part of the data. VICKEY is a system that can automatically mine conditional keys on large knowledge bases (KBs). For this, VICKEY combines techniques from key mining with techniques from rule mining. VICKEY can scale to knowledge bases of millions of facts. In addition, the conditional keys mined by VICKEY can improve the quality of entity linking by up to 47 percentage points.

## Downloads

We ran two rounds of experiments to assess VICKEY. The first round, which we call _runtime_, aims at evaluating VICKEY's scability. For this purpose we ran the system on 9 DBpedia classes and reported their runtimes. We compared the VICKEY with a rule mining method based on the [AMIE](https://www.mpi-inf.mpg.de/departments/databases-and-information-systems/research/yago-naga/amie/) system. The second round, called _linking_, aims at showing the benefit of conditional keys in the task of entity linking between ontologies. For this purpose we evaluated the precision and recall of a data linking using (a) classical keys, (b) conditional keys, and (c) a combination of both.



You can use the [editor on GitHub](https://github.com/lgalarra/vickey/edit/master/README.md) to maintain and preview the content for your website in Markdown files.

Whenever you commit to this repository, GitHub Pages will run [Jekyll](https://jekyllrb.com/) to rebuild the pages in your site, from the content in your Markdown files.

### Markdown

Markdown is a lightweight and easy-to-use syntax for styling your writing. It includes conventions for

```markdown
Syntax highlighted code block

# Header 1
## Header 2
### Header 3

- Bulleted
- List

1. Numbered
2. List

**Bold** and _Italic_ and `Code` text

[Link](url) and ![Image](src)
```

For more details see [GitHub Flavored Markdown](https://guides.github.com/features/mastering-markdown/).

### Jekyll Themes

Your Pages site will use the layout and styles from the Jekyll theme you have selected in your [repository settings](https://github.com/lgalarra/vickey/settings). The name of this theme is saved in the Jekyll `_config.yml` configuration file.

### Support or Contact

Having trouble with Pages? Check out our [documentation](https://help.github.com/categories/github-pages-basics/) or [contact support](https://github.com/contact) and we’ll help you sort it out.
