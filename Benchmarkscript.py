import os
runCommand = "java -Xms4G -Xmx8G -cp ./dist/ConcurrentSet.jar se.chalmers.dcs.bapic.concurrentset.test.BenchMark" 
testingSanity = ["true", "false"] #only check for first run
algorithms = ["KBST", "TrevorBrown", "EFRBLFBST", "NMLFBST", "HelpOptimalLocalRestartLFBST"]
threads = [1, 2, 3, 4, 5, 6, 7, 8]
insertheavy = " -i 60 -r 20 -x 20"
searchheavy = " -i 30 -r 50 -x 20"
deleteheavy = " -i 40 -r 10 -x 50"
uniform = " -i 34 -r 33 -x 33"
operations = [["Uniform", uniform], ["InsertHeavy", insertheavy], ["SearchHeavy", searchheavy], ["DeleteHeavy", deleteheavy]]

# Sanity Check
print("Running Sanity Checks")
for algo in algorithms:
	command = runCommand + " -t true " + " -a " + algo
	os.system(command)
	
# Benchmark Tests
print("Running Benchmark Tests")
for algo in algorithms:
	directory = "BenchmarkTests/" + algo + "/"
	os.system("mkdir " + directory[:-1])
	for op in operations:
		os.system("mkdir " + directory + op[0])
		for thread in threads:
			command = runCommand + " -a " + algo + " -n " + str(thread) + op[1]
			outputFile = directory + op[0] + "/" + str(thread) + "_" + op[0] + ".txt"
			os.system(command + " &> " + outputFile + str(thread) + "_" + op[0] + ".txt")
		print(algo + op[0] + " Run successful")

