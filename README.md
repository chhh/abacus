# Abacus

Abacus is a computational tool for extracting label-free quantitative information (spectral counts) from MS/MS data sets. 

Abacus aggregates data from multiple experiments, adjusts spectral counts to accurately account for peptides shared across multiple proteins, and performs common normalization steps. It can also output the spectral count data at the gene level, thus simplifying the integration and comparison between gene and protein expression data. Abacus is compatible with the widely used [Trans-Proteomic Pipeline](http://tools.proteomecenter.org/wiki/index.php?title=Software:TPP) suite of tools and comes with a graphical user interface making it easy to interact with the program. The main aim of Abacus is to streamline the analysis of spectral count data by providing an automated, easy to use solution for extracting this information from proteomic data sets for subsequent, more sophisticated statistical analysis.

The current version of Abacus provides both a graphical and command line interfaces to the software.


## Input
Abacus accepts separate *.pep.xml* files as input and a single master list *prot.xml*, potentially with a gene mapping file to map proteins to genes.
