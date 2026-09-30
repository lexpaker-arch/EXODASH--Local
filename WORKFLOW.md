# WORKFLOW - EXODASH

## Matriz (publicar no GitHub)

1. `./trocar-carro.sh limpar`          (remove JSON)
2. `./gradlew clean assembleDebug`
3. `git add -A && git commit -m "..."`
4. `git push origin main`
5. `git tag vX.Y.Z && git push origin vX.Y.Z`

**Resultado:** matriz sem JSON. Publica no GitHub.

## Custom (dar para alguém, NÃO publicar)

1. `./trocar-carro.sh palio_1998`     (coloca JSON)
2. `./gradlew clean assembleDebug`
3. Baixar o APK manualmente
4. Entregar o APK para a pessoa

**NUNCA fazer commit ou push com o JSON no assets.**

## Segurança

- `app/src/main/assets/vehicle.json` está no `.gitignore`
- Mesmo se você esquecer, o JSON não vai para o GitHub
- A keystore também está no `.gitignore`
