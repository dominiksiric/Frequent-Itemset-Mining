del /Q slave_address
start cmd /k java -cp ./out/production/untitled dp3.ms.SlaveSimulator0
start cmd /k java -cp ./out/production/untitled dp3.ms.SlaveSimulator1
start cmd /k java -cp ./out/production/untitled dp3.ms.Master -ff testing -st 0.1 -pc 1