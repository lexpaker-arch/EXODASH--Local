#!/bin/bash
# ============================================
# EXODASH - Troca de manual do veiculo
# Uso: ./trocar-carro.sh <nome_do_carro>
# Ex:  ./trocar-carro.sh focus_2001
# ============================================

CARRO=$1

if [ -z "$CARRO" ]; then
    echo "Uso: ./trocar-carro.sh <nome_do_carro>"
    echo ""
    echo "Manuais disponiveis:"
    ls custom/ 2>/dev/null | sed 's/^/  - /'
    exit 1
fi

if [ ! -f "custom/$CARRO/vehicle.json" ]; then
    echo "❌ Manual nao encontrado: custom/$CARRO/vehicle.json"
    echo ""
    echo "Manuais disponiveis:"
    ls custom/ 2>/dev/null | sed 's/^/  - /'
    exit 1
fi

# Remover manual anterior (se existir)
rm -f app/src/main/assets/vehicle.json

# Copiar novo
cp "custom/$CARRO/vehicle.json" app/src/main/assets/vehicle.json

echo "✅ Manual trocado para: $CARRO"
echo ""
echo "Detalhes:"
python3 -c "
import json
with open('app/src/main/assets/vehicle.json') as f:
    d = json.load(f)
print(f\"  Veiculo: {d.get('metadados', {}).get('veiculo_alvo', '?')}\")
print(f\"  DTCs: {len(d.get('falhas_registradas', []))}\")
print(f\"  Falhas ocultas: {len(d.get('falhas_ocultas', []))}\")
"
echo ""
echo "Proximo passo: ./gradlew clean assembleDebug"
