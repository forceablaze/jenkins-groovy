
### prepare
```
pip3 install -r requirements.txt
```

### コマンド
```
./execute.py \
    -u <user id> \
    -t <user token> \
	-U <Jenkins URL> \
	-g <groovy script path>
	(-a <JSON String>)
```

### example execute master script
```
./execute.py -u 12345678 -t abcdefg -U http://daifuku.hon.olympus.co.jp/jenkins -g master/printSlaveInfo.groovy
```

### example execute slave script
```
./execute.py -u 12345678 -t abcdefg -U http://daifuku.hon.olympus.co.jp/jenkins -g slave/printSlaveInfoOnNode.groovy -a '{"node"; "nodename"}'
```
