# VICKEY: Mining Conditional Keys on Knowledge Bases

## Introduction
A conditional key is a key constraint that is valid in only a part of the data. VICKEY is a system that can automatically mine conditional keys on large knowledge bases (KBs). For this, VICKEY combines techniques from key mining with techniques from rule mining. VICKEY can scale to knowledge bases of millions of facts. In addition, the conditional keys mined by VICKEY can improve the quality of entity linking by up to 47 percentage points.

## Downloads

We ran two rounds of experiments to assess VICKEY. The first round, which we call _runtime_, aims at evaluating VICKEY's scability. For this purpose we ran the system on 9 DBpedia classes and reported their runtimes. We compared the VICKEY with a rule mining method based on the [AMIE](https://www.mpi-inf.mpg.de/departments/databases-and-information-systems/research/yago-naga/amie/) system. The second round, called _linking_, aims at showing the benefit of conditional keys in the task of entity linking between ontologies. For this purpose we evaluated the precision and recall of a data linking using (a) classical keys, (b) conditional keys, and (c) a combination of both. 

### Downloads runtime
- [Datasets and conditional keys](https://www.dropbox.com/s/kiamr99gxlbbcqx/runtime.techreport.tar.gz?dl=0) from the technical report
- [Datasets and conditional keys]() from the ISWC paper
- [VICKEY binary](https://www.dropbox.com/s/vorab2s53yzy8se/VICKEY.jar?dl=0) and [instructions](https://www.dropbox.com/s/b55vcf357zde4cp/README-VICKEY?dl=0) on how to use it
- [Conditional key miner based on AMIE](https://www.dropbox.com/s/9prcl90huvtp35h/AMIECKMiner.jar?dl=0) and [instructions]() on how to use it. 
### Downloads linking
- [Datasets and conditional keys]()
 
