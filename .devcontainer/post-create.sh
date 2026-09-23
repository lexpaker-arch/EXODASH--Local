#!/bin/bash
set -e

echo "🚀 Configurando ambiente Android..."

# 1. Forçar o uso do Java 17
JAVA17=$(find /usr/lib/jvm -maxdepth 1 -name "java-17*" -type d | head -n 1)
if [ -n "$JAVA17" ]; then
    echo "export JAVA_HOME=$JAVA17" >> ~/.bashrc
    echo 'export PATH=$JAVA_HOME/bin:$PATH' >> ~/.bashrc
    export JAVA_HOME=$JAVA17
    export PATH=$JAVA_HOME/bin:$PATH
fi

# 2. Definir ANDROID_HOME se não existir
if [ -z "$ANDROID_HOME" ]; then
    export ANDROID_HOME=/opt/android-sdk
    echo 'export ANDROID_HOME=/opt/android-sdk' >> ~/.bashrc
fi

# 3. Adicionar ferramentas ao PATH
echo 'export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools' >> ~/.bashrc
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

# 4. Aceitar licenças do SDK
if command -v sdkmanager &> /dev/null; then
    echo "📜 Aceitando licenças do Android SDK..."
    yes | sdkmanager --licenses > /dev/null 2>&1 || true

    echo "📥 Instalando pacotes do SDK..."
    sdkmanager "platforms;android-34" "build-tools;34.0.0" > /dev/null 2>&1 || true
fi

echo "✅ Ambiente Android configurado com sucesso!"
