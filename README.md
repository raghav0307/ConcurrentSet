# Concurrent Set

This repository has been forked from Dr. Bapi's ConcurrentSet github repository. We would like to thank him for making this resource available for us.

We have added two classes to this repository, KBST and LockFreeKSTRQ. KBST has been designed and implemented by us. It is a lock-free K-ary search tree using edge level synchronization. LockFreeKSTRQ is taken from Trevor Brown's webpage and we have ported it into this repository.

## Instructions for running the code

Step 1: Build the repository
```
$ ant
```

Step 2.a: Run Sanity Tests
```
$ java -d64 -Xms4G -Xmx8G -cp ./dist/ConcurrentSet.jar se.chalmers.dcs.bapic.concurrentset.test.BenchMark -a KBST -t true
```

Step 2.b: Run Benchmark Tests
```
$ java -d64 -Xms4G -Xmx8G -cp ./dist/ConcurrentSet.jar se.chalmers.dcs.bapic.concurrentset.test.BenchMark -a KBST 
```

## Instructions for reproducing the evaluation experiment

Step 1: Build the repository
```
$ ant
```

Step 2: Run Benchmark test with different parameters
```
$ python3 Benchmarkscript.py
$ python3 KBSTvsTrevor.py
```

Step 3: Plot graphs

Use the following jupyter notebooks `PlotsScript.ipynb` and `KBSTvsTrevorBrownPlotsScript.ipynb` to generate the graphs.
