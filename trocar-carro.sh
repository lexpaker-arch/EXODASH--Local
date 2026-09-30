#!/bin/bash
# ============================================
# EXODASH - Troca de manual do veiculo
# Uso: ./trocar-carro.sh <nome_do_carro>
#      ./trocar-carro.sh limpar   (remove o JSON)
# ============================================

CARRO=$1

if [ "$CARRO" == "limpar" ]; then
    rm -f app/src/main/assets/vehicle.json
    echo "✅ JSON removido dos assets"
    echo "   A proxima build sera a MATRIZ (sem manual)"
    echo ""
    echo "Proximo passo: ./gradlew clean assembleDebug"
    exit 0
fi

if [ -z "$CARRO" ]; then
    echo "Uso: ./trocar-carro.sh <nome_do_carro>"
    echo "     ./trocar-carro.sh limpar"
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

rm -f app/src/main/assets/vehicle.json
cp "custom/$CARRO/vehicle.json" app/src/main/assets/vehicle.json

echo "✅ Manual trocado para: $CARRO"
echo ""
python3 -c "
import json
with open('app/src/main/assets/vehicle.json') as f:
    d = json.load(f)
print(f\"  Veiculo: {d.get('metadados', {}).get('veiculo_alvo', '?')}\")
print(f\"  DTCs: {len(d.get('falhas_registradas', []))}\")
print(f\"  Falhas ocultas: {len(d.get('falhas_ocultas', []))}\")
"
echo ""
echo "⚠ IMPORTANTE:"
echo "  - Este build NAO deve ser publicado no GitHub"
echo "  - Para voltar a matriz, rode: ./trocar-carro.sh limpar"
echo ""
echo "Proximo passo: ./gradlew clean assembleDebug"
