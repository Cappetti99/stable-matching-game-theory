# File Dati CSV

Questa cartella contiene i file CSV generati dai workflow generators e utilizzati dagli algoritmi di stable matching.

## 📄 Formato File

### `task.csv`
Formato: `t{ID} size`
```
t1 5
t2 3
t3 8
...
```

### `dag.csv` 
Formato: `t{from} t{to}`
```
t1 t2
t1 t3
t2 t4
...
```

### `vm.csv`
Matrice di bandwidth tra VM:
```
# vm1 vm2 vm3 vm4
vm1 0.0 150.2 200.1 180.5
vm2 150.2 0.0 175.3 190.8
vm3 200.1 175.3 0.0 220.4
vm4 180.5 190.8 220.4 0.0
```

### `processing_capacity.csv`
Formato: `vm{ID} capacity`
```
vm1 15.0
vm2 30.0
vm3 60.0
vm4 120.0
...
```

## 🔄 Workflow di Utilizzo

1. Eseguire un generatore dalla cartella `../generators/`
2. I file CSV vengono automaticamente creati qui
3. Gli algoritmi in `../algorithms/` leggono questi file
4. Risultati mostrati nel terminale

## 🗂️ File Temporanei

I file in questa cartella vengono sovrascritti ad ogni esecuzione dei generatori. Per preservare specifici dataset, copiarli in una sottocartella dedicata.
