#!/bin/bash
PROJ="/workspaces/EXODASH--Local"
SRC="$PROJ/app/src/main/java/com/exodash"
RES="$PROJ/app/src/main/res"
MANIFEST="$PROJ/app/src/main/AndroidManifest.xml"

V='\033[0;31m'
A='\033[0;33m'
G='\033[0;32m'
B='\033[0;34m'
N='\033[0m'

echo "========================================"
echo "  EXODASH - VARREDURA"
echo "========================================"
echo ""

echo "[1] IDs codigo vs layout"
IDS_KOTLIN=$(grep -oh "R\.id\.[a-zA-Z_]*" $SRC/*.kt | sort -u | sed 's/R.id.//')
IDS_LAYOUT=$(grep -oh 'android:id="@+id/[a-zA-Z_]*"' $RES/layout/*.xml | sed 's/.*@+id\///;s/"//' | sort -u)
FALTANDO=0
for id in $IDS_KOTLIN; do
    if ! echo "$IDS_LAYOUT" | grep -q "^$id$"; then
        printf "  ${V}x FALTA: $id${N}\n"
        FALTANDO=$((FALTANDO+1))
    fi
done
[ $FALTANDO -eq 0 ] && printf "  ${G}OK - Todos os IDs existem${N}\n"
echo ""

echo "[2] Drawables"
DRAW_USADOS=$(grep -oh "@drawable/[a-zA-Z_]*" $RES/layout/*.xml $RES/values/*.xml $MANIFEST 2>/dev/null | sed 's/.*@drawable\///' | sort -u)
DRAW_EXIST=$(ls $RES/drawable/ 2>/dev/null | sed 's/\.xml$//' | sort -u)
FALTA_DRAW=0
for d in $DRAW_USADOS; do
    if ! echo "$DRAW_EXIST" | grep -q "^$d$"; then
        printf "  ${V}x FALTA: $d${N}\n"
        FALTA_DRAW=$((FALTA_DRAW+1))
    fi
done
[ $FALTA_DRAW -eq 0 ] && printf "  ${G}OK - Todos os drawables existem${N}\n"
echo ""

echo "[3] Permissoes manifest vs uso"
PERMS=$(grep "uses-permission" $MANIFEST | sed 's/.*android:name="android.permission.//;s/".*//')
for p in $PERMS; do
    if grep -q "$p" $SRC/*.kt 2>/dev/null; then
        printf "  ${G}OK $p${N}\n"
    else
        printf "  ${A}? $p - nao usada no codigo${N}\n"
    fi
done
echo ""

echo "[4] Handlers sem removeCallbacks"
for f in $SRC/*.kt; do
    if grep -q "Handler" "$f"; then
        if ! grep -q "removeCallbacks" "$f"; then
            printf "  ${A}? $(basename $f): Handler sem removeCallbacks${N}\n"
        fi
    fi
done
echo ""

echo "[5] APIs deprecated"
if grep -q "AsyncTask" $SRC/*.kt; then
    printf "  ${A}? AsyncTask usado${N}\n"
fi
if grep -q "onBackPressed" $SRC/*.kt; then
    printf "  ${A}? onBackPressed sobrescrito${N}\n"
fi
echo ""

echo "[6] Activities no manifest"
ACT_FILES=$(grep -oh "^class [A-Za-z]*Activity" $SRC/*.kt | sed 's/class //' | sort -u)
for a in $ACT_FILES; do
    if grep -q "\.$a" $MANIFEST; then
        printf "  ${G}OK $a${N}\n"
    else
        printf "  ${V}x $a NAO esta no manifest${N}\n"
    fi
done
echo ""

echo "[7] Chave de API em logs"
if grep -qE "Log.*(groq|apiKey|GROQ)" $SRC/*.kt 2>/dev/null; then
    printf "  ${V}x Chave pode estar em log${N}\n"
else
    printf "  ${G}OK - Sem log de chave${N}\n"
fi
echo ""

echo "[8] JSON de DTCs"
if [ -f "$PROJ/app/src/main/assets/dtc_codes.json" ]; then
    if python3 -c "import json; json.load(open('$PROJ/app/src/main/assets/dtc_codes.json'))" 2>/dev/null; then
        printf "  ${G}OK - JSON valido${N}\n"
    else
        printf "  ${V}x JSON invalido${N}\n"
    fi
else
    printf "  ${V}x dtc_codes.json nao encontrado${N}\n"
fi
echo ""

echo "========================================"
echo "RESUMO"
echo "========================================"
echo "Kotlin:  $(ls $SRC/*.kt | wc -l) arquivos"
echo "Layouts: $(ls $RES/layout/*.xml | wc -l)"
echo "Drawables: $(ls $RES/drawable/*.xml 2>/dev/null | wc -l)"
echo "Linhas:  $(cat $SRC/*.kt | wc -l)"
echo ""
printf "IDs faltando:     $FALTANDO\n"
printf "Drawables faltando: $FALTA_DRAW\n"
echo ""
echo "========================================"
