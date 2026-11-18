#!/bin/bash
set -e
cd /workspaces/EXODASH--Local

RES="app/src/main/res/values"
DRAW="app/src/main/res/drawable"
mkdir -p "$DRAW"

# --- colors.xml ---
python3 - <<'PY'
p = "app/src/main/res/values/colors.xml"
s = open(p).read()
add = '''    <color name="gray">#888888</color>
    <color name="dark_gray">#1A1A1A</color>
    <color name="cyan_dim">#6600FFCC</color>
    <color name="red_dim">#66FF3333</color>
    <color name="cyan_pressed">#00CCAA</color>
'''
if 'name="gray"' not in s:
    s = s.replace('</resources>', add + '</resources>')
    open(p, 'w').write(s)
    print("OK colors.xml: 5 cores adicionadas")
else:
    print("SKIP colors.xml: ja tem gray")
PY

# --- dimens.xml ---
python3 - <<'PY'
p = "app/src/main/res/values/dimens.xml"
s = open(p).read()
add = '''    <dimen name="space_xs">4dp</dimen>
    <dimen name="space_sm">8dp</dimen>
    <dimen name="space_md">12dp</dimen>
    <dimen name="space_lg">16dp</dimen>
    <dimen name="space_xl">24dp</dimen>
    <dimen name="radius_sm">4dp</dimen>
    <dimen name="radius_md">8dp</dimen>
    <dimen name="radius_lg">12dp</dimen>
    <dimen name="stroke_thin">1dp</dimen>
    <dimen name="stroke_medium">2dp</dimen>
    <dimen name="touch_min">48dp</dimen>
'''
if 'name="space_md"' not in s:
    s = s.replace('</resources>', add + '</resources>')
    open(p, 'w').write(s)
    print("OK dimens.xml: 11 dimens adicionadas")
else:
    print("SKIP dimens.xml: ja tem space_md")
PY

# --- styles.xml ---
cat > "$RES/styles.xml" <<'XML'
<?xml version="1.0" encoding="utf-8"?>
<resources>

    <style name="ExoButton" parent="Widget.AppCompat.Button">
        <item name="android:background">@drawable/bg_button_cyan</item>
        <item name="android:textColor">@color/black</item>
        <item name="android:textSize">14sp</item>
        <item name="android:textStyle">bold</item>
        <item name="android:letterSpacing">0.1</item>
        <item name="android:minHeight">@dimen/touch_min</item>
        <item name="android:paddingStart">@dimen/space_lg</item>
        <item name="android:paddingEnd">@dimen/space_lg</item>
        <item name="android:gravity">center</item>
        <item name="android:textAllCaps">false</item>
    </style>

    <style name="ExoButton.Danger">
        <item name="android:background">@drawable/bg_button_red</item>
        <item name="android:textColor">@color/white</item>
    </style>

    <style name="ExoButton.Ghost">
        <item name="android:background">@drawable/bg_button_ghost</item>
        <item name="android:textColor">@color/cyan</item>
    </style>

    <style name="ExoLabel">
        <item name="android:textColor">@color/cyan</item>
        <item name="android:textSize">11sp</item>
        <item name="android:textStyle">bold</item>
        <item name="android:letterSpacing">0.15</item>
        <item name="android:textAllCaps">true</item>
    </style>

    <style name="ExoValue">
        <item name="android:textColor">@color/white</item>
        <item name="android:textSize">14sp</item>
    </style>

    <style name="ExoTitle">
        <item name="android:textColor">@color/cyan</item>
        <item name="android:textSize">18sp</item>
        <item name="android:textStyle">bold</item>
        <item name="android:letterSpacing">0.2</item>
    </style>

    <style name="ExoHeader">
        <item name="android:layout_width">match_parent</item>
        <item name="android:layout_height">wrap_content</item>
        <item name="android:orientation">horizontal</item>
        <item name="android:gravity">center_vertical</item>
        <item name="android:paddingStart">@dimen/space_md</item>
        <item name="android:paddingEnd">@dimen/space_md</item>
        <item name="android:paddingTop">@dimen/space_md</item>
        <item name="android:paddingBottom">@dimen/space_md</item>
        <item name="android:background">@color/black</item>
    </style>

    <style name="ExoBackButton">
        <item name="android:layout_width">wrap_content</item>
        <item name="android:layout_height">@dimen/touch_min</item>
        <item name="android:text">← VOLTAR</item>
        <item name="android:textColor">@color/cyan</item>
        <item name="android:textSize">12sp</item>
        <item name="android:textStyle">bold</item>
        <item name="android:letterSpacing">0.1</item>
        <item name="android:background">@android:color/transparent</item>
        <item name="android:paddingStart">@dimen/space_sm</item>
        <item name="android:paddingEnd">@dimen/space_sm</item>
    </style>

    <style name="ExoCard">
        <item name="android:layout_width">match_parent</item>
        <item name="android:layout_height">wrap_content</item>
        <item name="android:background">@drawable/bg_card</item>
        <item name="android:padding">@dimen/space_md</item>
        <item name="android:layout_marginBottom">@dimen/space_sm</item>
    </style>

    <style name="ExoDivider">
        <item name="android:layout_width">match_parent</item>
        <item name="android:layout_height">@dimen/stroke_thin</item>
        <item name="android:background">@drawable/divider_gradient</item>
        <item name="android:layout_marginTop">@dimen/space_sm</item>
        <item name="android:layout_marginBottom">@dimen/space_sm</item>
    </style>

</resources>
XML
echo "OK styles.xml criado"

# --- drawables ---
cat > "$DRAW/bg_button_cyan.xml" <<'XML'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="@color/cyan"/>
    <corners android:radius="@dimen/radius_md"/>
</shape>
XML

cat > "$DRAW/bg_button_red.xml" <<'XML'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="@color/red"/>
    <corners android:radius="@dimen/radius_md"/>
</shape>
XML

cat > "$DRAW/bg_button_ghost.xml" <<'XML'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="@android:color/transparent"/>
    <stroke android:width="@dimen/stroke_thin" android:color="@color/cyan"/>
    <corners android:radius="@dimen/radius_md"/>
</shape>
XML

cat > "$DRAW/bg_card.xml" <<'XML'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="@color/dark_gray"/>
    <stroke android:width="@dimen/stroke_thin" android:color="@color/cyan_dim"/>
    <corners android:radius="@dimen/radius_md"/>
</shape>
XML

echo "OK 4 drawables criados"
echo ""
echo "=== FIM ==="
