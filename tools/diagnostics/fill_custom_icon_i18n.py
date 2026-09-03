from __future__ import annotations

import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RES_ROOT = ROOT / "app" / "src" / "main" / "res"

# 英文基准
EN_DATA = {
    "custom_icon": "Custom Icon",
    "custom_icon_import": "Import Icon Pack",
    "custom_icon_reset": "Reset to Default",
    "custom_icon_preview_shortcut": "Shortcut Icon",
    "custom_icon_shortcut_subtitle": "Android does not support modifying the software icon. Please fork and modify if needed!",
    "custom_icon_preview_tile": "Quick Settings Tile",
    "custom_icon_apply_pack": "Apply This Icon Pack",
    "custom_icon_apply_success": "Icon pack applied. Desktop shortcut requested.",
    "custom_icon_pin_shortcut_not_supported": "Your launcher does not support pinning shortcuts.",
    "custom_icon_no_pack_to_apply": "Please import an icon pack first.",
    "custom_icon_tile_state_active": "Active (Tap to toggle)",
    "custom_icon_tile_state_inactive": "Inactive (Tap to toggle)",
    "custom_icon_import_success": "Icon pack imported successfully",
    "custom_icon_reset_success": "Default icons restored",
    "custom_icon_reset_confirm": "Are you sure you want to restore default icons?",
    "custom_icon_error_missing": "Icon pack must contain: %s",
    "custom_icon_error_dimension": "%1$s must be 512x512 pixels (current: %2$dx%3$d)",
    "custom_icon_error_not_png": "%s is not a valid PNG image",
    "custom_icon_error_security": "Security verification failed: %s",
    "custom_icon_requirement_tip": "Icon pack must be a ZIP archive containing 512x512 icon.png and tile.png.",
}

ZH_TRADITIONAL = {
    "custom_icon": "自訂圖示",
    "custom_icon_import": "匯入圖示包",
    "custom_icon_reset": "恢復預設",
    "custom_icon_preview_shortcut": "捷徑圖示",
    "custom_icon_shortcut_subtitle": "Android平台不支援修改軟體圖示，若有需要請自行fork後修改！",
    "custom_icon_preview_tile": "快速設定圖示",
    "custom_icon_apply_pack": "套用此圖示包",
    "custom_icon_apply_success": "已套用圖示包並請求建立桌面捷徑。",
    "custom_icon_pin_shortcut_not_supported": "目前桌面啟動器不支援建立桌面捷徑。",
    "custom_icon_no_pack_to_apply": "請先匯入圖示包。",
    "custom_icon_tile_state_active": "開啟狀態（點擊切換）",
    "custom_icon_tile_state_inactive": "關閉狀態（點擊切換）",
    "custom_icon_import_success": "圖示包匯入成功",
    "custom_icon_reset_success": "已恢復預設圖示",
    "custom_icon_reset_confirm": "確定要恢復預設圖示嗎？",
    "custom_icon_error_missing": "圖示包缺少必要檔案：%s",
    "custom_icon_error_dimension": "%1$s 解析度必須為 512x512 像素（目前為：%2$dx%3$d）",
    "custom_icon_error_not_png": "%s 不是有效的 PNG 圖片檔案",
    "custom_icon_error_security": "安全檢查失敗：%s",
    "custom_icon_requirement_tip": "圖示包須為 ZIP 壓縮包，內含 512x512 的 icon.png 與 tile.png。",
}

JA_DATA = {
    "custom_icon": "カスタムアイコン",
    "custom_icon_import": "アイコンパックをインポート",
    "custom_icon_reset": "デフォルトに戻す",
    "custom_icon_preview_shortcut": "ショートカットアイコン",
    "custom_icon_shortcut_subtitle": "Androidプラットフォームはアプリアイコンの変更をサポートしていません。必要に応じてご自身でforkして変更してください！",
    "custom_icon_preview_tile": "クイック設定タイル",
    "custom_icon_apply_pack": "このアイコンパックを適用",
    "custom_icon_apply_success": "アイコンパックを適用し、ホーム画面のショートカット作成をリクエストしました。",
    "custom_icon_pin_shortcut_not_supported": "現在のランチャーはショートカットの固定をサポートしていません。",
    "custom_icon_no_pack_to_apply": "最初にアイコンパックをインポートしてください。",
    "custom_icon_tile_state_active": "有効（タップして切り替え）",
    "custom_icon_tile_state_inactive": "無効（タップして切り替え）",
    "custom_icon_import_success": "アイコンパックが正常にインポートされました",
    "custom_icon_reset_success": "デフォルトのアイコンに戻しました",
    "custom_icon_reset_confirm": "デフォルトのアイコンに戻してもよろしいですか？",
    "custom_icon_error_missing": "アイコンパックに必要なファイルがありません：%s",
    "custom_icon_error_dimension": "%1$s の解像度は 512x512 ピクセルである必要があります（現在：%2$dx%3$d）",
    "custom_icon_error_not_png": "%s は有効な PNG 画像ファイルではありません",
    "custom_icon_error_security": "セキュリティ検証に失敗しました：%s",
    "custom_icon_requirement_tip": "アイコンパックは 512x512 の icon.png と tile.png を含む ZIP アーカイブである必要があります。",
}

KO_DATA = {
    "custom_icon": "사용자 정의 아이콘",
    "custom_icon_import": "아이콘 팩 가져오기",
    "custom_icon_reset": "기본값으로 복원",
    "custom_icon_preview_shortcut": "바로가기 아이콘",
    "custom_icon_shortcut_subtitle": "Android 플랫폼은 앱 아이콘 수정을 지원하지 않습니다. 필요한 경우 직접 fork하여 수정하세요!",
    "custom_icon_preview_tile": "빠른 설정 타일",
    "custom_icon_apply_pack": "이 아이콘 팩 적용",
    "custom_icon_apply_success": "아이콘 팩이 적용되었으며 홈 화면 바로가기 생성을 요청했습니다.",
    "custom_icon_pin_shortcut_not_supported": "현재 런처에서 바로가기 고정을 지원하지 않습니다. ",
    "custom_icon_no_pack_to_apply": "먼저 아이콘 팩을 가져오세요.",
    "custom_icon_tile_state_active": "활성화됨 (탭하여 전환)",
    "custom_icon_tile_state_inactive": "비활성화됨 (탭하여 전환)",
    "custom_icon_import_success": "아이콘 팩을 성공적으로 가져왔습니다",
    "custom_icon_reset_success": "기본 아이콘으로 복원되었습니다",
    "custom_icon_reset_confirm": "기본 아이콘으로 복원하시겠습니까?",
    "custom_icon_error_missing": "아이콘 팩에 필수 파일이 없습니다: %s",
    "custom_icon_error_dimension": "%1$s 해상도는 512x512 픽셀이어야 합니다 (현재: %2$dx%3$d)",
    "custom_icon_error_not_png": "%s 은(는) 유효한 PNG 이미지 파일이 아닙니다",
    "custom_icon_error_security": "보안 검증 실패: %s",
    "custom_icon_requirement_tip": "아이콘 팩은 512x512의 icon.png 및 tile.png를 포함하는 ZIP 파일이어야 합니다.",
}

RU_DATA = {
    "custom_icon": "Пользовательские иконки",
    "custom_icon_import": "Импортировать набор иконок",
    "custom_icon_reset": "Сбросить по умолчанию",
    "custom_icon_preview_shortcut": "Иконка ярлыка",
    "custom_icon_shortcut_subtitle": "Платформа Android не поддерживает изменение иконки приложения. При необходимости сделайте fork и измените самостоятельно!",
    "custom_icon_preview_tile": "Плитка быстрых настроек",
    "custom_icon_apply_pack": "Применить этот набор иконок",
    "custom_icon_apply_success": "Набор иконок применен. Запрошено создание ярлыка на рабочем столе.",
    "custom_icon_pin_shortcut_not_supported": "Текущий лаунчер не поддерживает закрепление ярлыков.",
    "custom_icon_no_pack_to_apply": "Сначала импортируйте набор иконок.",
    "custom_icon_tile_state_active": "Включено (нажмите для переключения)",
    "custom_icon_tile_state_inactive": "Выключено (нажмите для переключения)",
    "custom_icon_import_success": "Набор иконок успешно импортирован",
    "custom_icon_reset_success": "Восстановлены иконки по умолчанию",
    "custom_icon_reset_confirm": "Вы уверены, что хотите восстановить иконки по умолчанию?",
    "custom_icon_error_missing": "В наборе иконок отсутствует обязательный файл: %s",
    "custom_icon_error_dimension": "Разрешение %1$s должно быть 512x512 пикселей (текущее: %2$dx%3$d)",
    "custom_icon_error_not_png": "%s не является корректным изображением PNG",
    "custom_icon_error_security": "Ошибка проверки безопасности: %s",
    "custom_icon_requirement_tip": "Набор иконок должен быть ZIP-архивом, содержащим icon.png и tile.png размером 512x512.",
}

UK_DATA = {
    "custom_icon": "Користувацькі іконки",
    "custom_icon_import": "Імпортувати набір іконок",
    "custom_icon_reset": "Скинути за замовчуванням",
    "custom_icon_preview_shortcut": "Іконка ярлика",
    "custom_icon_shortcut_subtitle": "Платформа Android не підтримує зміну іконки програми. За потреби зробіть fork та змініть самостійно!",
    "custom_icon_preview_tile": "Плитка швидких налаштувань",
    "custom_icon_apply_pack": "Застосувати цей набір іконок",
    "custom_icon_apply_success": "Набір іконок застосовано. Запитано створення ярлика на робочому столі.",
    "custom_icon_pin_shortcut_not_supported": "Поточний лаунчер не підтримує закріплення ярликів.",
    "custom_icon_no_pack_to_apply": "Спочатку імпортуйте набір іконок.",
    "custom_icon_tile_state_active": "Увімкнено (натисніть для перемикання)",
    "custom_icon_tile_state_inactive": "Вимкнено (натисніть для перемикання)",
    "custom_icon_import_success": "Набір іконок успішно імпортовано",
    "custom_icon_reset_success": "Відновлено іконки за замовчуванням",
    "custom_icon_reset_confirm": "Ви впевнені, що хочете відновити іконки за замовчуванням?",
    "custom_icon_error_missing": "У наборі іконок відсутній обов'язковий файл: %s",
    "custom_icon_error_dimension": "Роздільна здатність %1$s має бути 512x512 пікселів (поточна: %2$dx%3$d)",
    "custom_icon_error_not_png": "%s не є коректним зображенням PNG",
    "custom_icon_error_security": "Помилка перевірки безпеки: %s",
    "custom_icon_requirement_tip": "Набір іконок має бути ZIP-архівом, що містить icon.png та tile.png розміром 512x512.",
}

DE_DATA = {
    "custom_icon": "Benutzerdefiniertes Symbol",
    "custom_icon_import": "Symbolpaket importieren",
    "custom_icon_reset": "Auf Standard zurücksetzen",
    "custom_icon_preview_shortcut": "Verknüpfungssymbol",
    "custom_icon_shortcut_subtitle": "Die Android-Plattform unterstützt das Ändern von App-Symbolen nicht. Bei Bedarf bitte selbst forken und anpassen!",
    "custom_icon_preview_tile": "Schnelleinstellungskachel",
    "custom_icon_apply_pack": "Dieses Symbolpaket anwenden",
    "custom_icon_apply_success": "Symbolpaket angewendet. Desktop-Verknüpfung angefordert.",
    "custom_icon_pin_shortcut_not_supported": "Der aktuelle Launcher unterstützt das Anheften von Verknüpfungen nicht.",
    "custom_icon_no_pack_to_apply": "Bitte zuerst ein Symbolpaket importieren.",
    "custom_icon_tile_state_active": "Aktiv (Zum Umschalten tippen)",
    "custom_icon_tile_state_inactive": "Inaktiv (Zum Umschalten tippen)",
    "custom_icon_import_success": "Symbolpaket erfolgreich importiert",
    "custom_icon_reset_success": "Standardsymbole wiederhergestellt",
    "custom_icon_reset_confirm": "Möchten Sie die Standardsymbole wirklich wiederherstellen?",
    "custom_icon_error_missing": "Im Symbolpaket fehlt die Datei: %s",
    "custom_icon_error_dimension": "%1$s muss eine Auflösung von 512x512 Pixeln haben (aktuell: %2$dx%3$d)",
    "custom_icon_error_not_png": "%s ist keine gültige PNG-Datei",
    "custom_icon_error_security": "Sicherheitsprüfung fehlgeschlagen: %s",
    "custom_icon_requirement_tip": "Das Symbolpaket muss ein ZIP-Archiv sein, das icon.png und tile.png in 512x512 enthält.",
}

FR_DATA = {
    "custom_icon": "Icône personnalisée",
    "custom_icon_import": "Importer un pack d'icônes",
    "custom_icon_reset": "Rétablir par défaut",
    "custom_icon_preview_shortcut": "Icône du raccourci",
    "custom_icon_shortcut_subtitle": "La plateforme Android ne permet pas de modifier l'icône de l'application. Veuillez forker le projet pour la modifier !",
    "custom_icon_preview_tile": "Tuile des paramètres rapides",
    "custom_icon_apply_pack": "Appliquer ce pack d'icônes",
    "custom_icon_apply_success": "Pack d'icônes appliqué. Création du raccourci demandée.",
    "custom_icon_pin_shortcut_not_supported": "Le lanceur actuel ne prend pas en charge l'épinglage de raccourcis.",
    "custom_icon_no_pack_to_apply": "Veuillez d'abord importer un pack d'icônes.",
    "custom_icon_tile_state_active": "Actif (appuyer pour basculer)",
    "custom_icon_tile_state_inactive": "Inactif (appuyer pour basculer)",
    "custom_icon_import_success": "Pack d'icônes importé avec succès",
    "custom_icon_reset_success": "Icônes par défaut rétablies",
    "custom_icon_reset_confirm": "Voulez-vous vraiment rétablir les icônes par défaut ?",
    "custom_icon_error_missing": "Fichier requis manquant dans le pack d'icônes : %s",
    "custom_icon_error_dimension": "La résolution de %1$s doit être de 512x512 pixels (actuelle : %2$dx%3$d)",
    "custom_icon_error_not_png": "%s n'est pas une image PNG valide",
    "custom_icon_error_security": "Échec de la vérification de sécurité : %s",
    "custom_icon_requirement_tip": "Le pack d'icônes doit être une archive ZIP contenant icon.png et tile.png en 512x512.",
}

ES_DATA = {
    "custom_icon": "Icono personalizado",
    "custom_icon_import": "Importar paquete de iconos",
    "custom_icon_reset": "Restablecer valores predeterminados",
    "custom_icon_preview_shortcut": "Icono de acceso directo",
    "custom_icon_shortcut_subtitle": "La plataforma Android no admite modificar el icono de la aplicación. ¡Haga un fork si necesita cambiarlo!",
    "custom_icon_preview_tile": "Mosaico de ajustes rápidos",
    "custom_icon_apply_pack": "Aplicar este paquete de iconos",
    "custom_icon_apply_success": "Paquete de iconos aplicado. Se solicitó el acceso directo.",
    "custom_icon_pin_shortcut_not_supported": "Su lanzador no admite anclar accesos directos.",
    "custom_icon_no_pack_to_apply": "Primero importe un paquete de iconos.",
    "custom_icon_tile_state_active": "Activo (toca para cambiar)",
    "custom_icon_tile_state_inactive": "Inactivo (toca para cambiar)",
    "custom_icon_import_success": "Paquete de iconos importado con éxito",
    "custom_icon_reset_success": "Iconos predeterminados restaurados",
    "custom_icon_reset_confirm": "¿Está seguro de que desea restaurar los iconos predeterminados?",
    "custom_icon_error_missing": "Falta archivo requerido en el paquete: %s",
    "custom_icon_error_dimension": "La resolución de %1$s debe ser de 512x512 píxeles (actual: %2$dx%3$d)",
    "custom_icon_error_not_png": "%s no es una imagen PNG válida",
    "custom_icon_error_security": "Error de verificación de seguridad: %s",
    "custom_icon_requirement_tip": "El paquete de iconos debe ser un ZIP que contenga icon.png y tile.png de 512x512.",
}

PT_BR_DATA = {
    "custom_icon": "Ícone personalizado",
    "custom_icon_import": "Importar pacote de ícones",
    "custom_icon_reset": "Restaurar padrão",
    "custom_icon_preview_shortcut": "Ícone do atalho",
    "custom_icon_shortcut_subtitle": "O Android não suporta alterar o ícone do app. Se necessário, faça um fork e modifique você mesmo!",
    "custom_icon_preview_tile": "Bloco de configurações rápidas",
    "custom_icon_apply_pack": "Aplicar este pacote de ícones",
    "custom_icon_apply_success": "Pacote aplicado. Criação de atalho solicitada.",
    "custom_icon_pin_shortcut_not_supported": "Seu inicializador não suporta fixar atalhos.",
    "custom_icon_no_pack_to_apply": "Por favor, importe um pacote de ícones primeiro.",
    "custom_icon_tile_state_active": "Ativo (toque para alternar)",
    "custom_icon_tile_state_inactive": "Inativo (toque para alternar)",
    "custom_icon_import_success": "Pacote de ícones importado com sucesso",
    "custom_icon_reset_success": "Ícones padrão restaurados",
    "custom_icon_reset_confirm": "Tem certeza de que deseja restaurar os ícones padrão?",
    "custom_icon_error_missing": "O pacote deve conter o arquivo: %s",
    "custom_icon_error_dimension": "A resolução de %1$s deve ser 512x512 pixels (atual: %2$dx%3$d)",
    "custom_icon_error_not_png": "%s não é uma imagem PNG válida",
    "custom_icon_error_security": "Falha na verificação de segurança: %s",
    "custom_icon_requirement_tip": "O pacote deve ser um arquivo ZIP contendo icon.png e tile.png de 512x512.",
}

IT_DATA = {
    "custom_icon": "Icona personalizzata",
    "custom_icon_import": "Importa pacchetto di icone",
    "custom_icon_reset": "Ripristina predefiniti",
    "custom_icon_preview_shortcut": "Icona di scelta rapida",
    "custom_icon_shortcut_subtitle": "La piattaforma Android non supporta la modifica dell'icona dell'app. Esegui il fork se necessario!",
    "custom_icon_preview_tile": "Riquadro Impostazioni rapide",
    "custom_icon_apply_pack": "Applica questo pacchetto",
    "custom_icon_apply_success": "Pacchetto applicato. Creazione scorciatoia richiesta.",
    "custom_icon_pin_shortcut_not_supported": "Il tuo launcher non supporta il blocco delle scorciatoie.",
    "custom_icon_no_pack_to_apply": "Importa prima un pacchetto di icone.",
    "custom_icon_tile_state_active": "Attivo (tocca per alternare)",
    "custom_icon_tile_state_inactive": "Inattivo (tocca per alternare)",
    "custom_icon_import_success": "Pacchetto di icone importato con successo",
    "custom_icon_reset_success": "Icone predefinite ripristinate",
    "custom_icon_reset_confirm": "Sei sicuro di voler ripristinare le icone predefinite?",
    "custom_icon_error_missing": "Il pacchetto di icone deve contenere: %s",
    "custom_icon_error_dimension": "La risoluzione di %1$s deve essere di 512x512 pixel (attuale: %2$dx%3$d)",
    "custom_icon_error_not_png": "%s non è un'immagine PNG valida",
    "custom_icon_error_security": "Verifica di sicurezza fallita: %s",
    "custom_icon_requirement_tip": "Il pacchetto di icone deve essere un archivio ZIP contenente icon.png e tile.png di 512x512.",
}

NL_DATA = {
    "custom_icon": "Aangepast pictogram",
    "custom_icon_import": "Pictogrampakket importeren",
    "custom_icon_reset": "Standaardinstellingen herstellen",
    "custom_icon_preview_shortcut": "Snelkoppelingspictogram",
    "custom_icon_shortcut_subtitle": "Android ondersteunt het wijzigen van het app-pictogram niet. Fork en pas aan indien nodig!",
    "custom_icon_preview_tile": "Snelle instellingen-tegel",
    "custom_icon_apply_pack": "Dit pictogrampakket toepassen",
    "custom_icon_apply_success": "Pictogrampakket toegepast. Snelkoppeling aangevraagd.",
    "custom_icon_pin_shortcut_not_supported": "Uw startscherm ondersteunt het vastzetten van snelkoppelingen niet.",
    "custom_icon_no_pack_to_apply": "Importeer eerst een pictogrampakket.",
    "custom_icon_tile_state_active": "Actief (tik om te wisselen)",
    "custom_icon_tile_state_inactive": "Inactief (tik om te wisselen)",
    "custom_icon_import_success": "Pictogrampakket succesvol geïmporteerd",
    "custom_icon_reset_success": "Standaardpictogrammen hersteld",
    "custom_icon_reset_confirm": "Weet u zeker dat u de standaardpictogrammen wilt herstellen?",
    "custom_icon_error_missing": "Pictogrampakket moet het volgende bevatten: %s",
    "custom_icon_error_dimension": "%1$s moet 512x512 pixels zijn (huidig: %2$dx%3$d)",
    "custom_icon_error_not_png": "%s is geen geldige PNG-afbeelding",
    "custom_icon_error_security": "Beveiligingscontrole mislukt: %s",
    "custom_icon_requirement_tip": "Pictogrampakket moet een ZIP-bestand zijn met 512x512 icon.png en tile.png.",
}

TR_DATA = {
    "custom_icon": "Özel Simge",
    "custom_icon_import": "Simge Paketini İçe Aktar",
    "custom_icon_reset": "Varsayılana Sıfırla",
    "custom_icon_preview_shortcut": "Kısayol Simgesi",
    "custom_icon_shortcut_subtitle": "Android platformu uygulama simgesini değiştirmeyi desteklemez. Gerekirse kendiniz fork edin!",
    "custom_icon_preview_tile": "Hızlı Ayarlar Kutucuğu",
    "custom_icon_apply_pack": "Bu Simge Paketini Uygula",
    "custom_icon_apply_success": "Simge paketi uygulandı. Masaüstü kısayolu istendi.",
    "custom_icon_pin_shortcut_not_supported": "Başlatıcınız kısayol sabitlemeyi desteklemiyor.",
    "custom_icon_no_pack_to_apply": "Lütfen önce bir simge paketi içe aktarın.",
    "custom_icon_tile_state_active": "Etkin (Değiştirmek için dokunun)",
    "custom_icon_tile_state_inactive": "Devre dışı (Değiştirmek için dokunun)",
    "custom_icon_import_success": "Simge paketi başarıyla içe aktarıldı",
    "custom_icon_reset_success": "Varsayılan simgeler geri yüklendi",
    "custom_icon_reset_confirm": "Varsayılan simgeleri geri yüklemek istediğinizden emin misiniz?",
    "custom_icon_error_missing": "Simge paketi şunu içermelidir: %s",
    "custom_icon_error_dimension": "%1$s çözünürlüğü 512x512 piksel olmalıdır (mevcut: %2$dx%3$d)",
    "custom_icon_error_not_png": "%s geçerli bir PNG resmi değil",
    "custom_icon_error_security": "Güvenlik doğrulaması başarısız: %s",
    "custom_icon_requirement_tip": "Simge paketi 512x512 icon.png ve tile.png içeren bir ZIP arşivi olmalıdır.",
}

LANG_MAP = {
    "values-zh-rTW": ZH_TRADITIONAL,
    "values-zh-rHK": ZH_TRADITIONAL,
    "values-ja": JA_DATA,
    "values-ko": KO_DATA,
    "values-ru": RU_DATA,
    "values-uk": UK_DATA,
    "values-be": UK_DATA, # 近似斯拉夫语支
    "values-de": DE_DATA,
    "values-fr": FR_DATA,
    "values-es": ES_DATA,
    "values-pt-rBR": PT_BR_DATA,
    "values-it": IT_DATA,
    "values-nl": NL_DATA,
    "values-tr": TR_DATA,
}

def escape_xml(s: str) -> str:
    # 替换特殊字符并转义单引号如果需要
    s = s.replace("&", "&").replace("<", "<").replace(">", ">")
    s = s.replace("'", "\\'")
    return s

def append_to_file(filepath: Path, data: dict[str, str]) -> None:
    content = filepath.read_text(encoding="utf-8")
    if "</resources>" not in content:
        return
    
    lines_to_add = ["\n    <!-- Custom Icon Pack -->"]
    for k, v in data.items():
        escaped_v = escape_xml(v)
        lines_to_add.append(f'    <string name="{k}">{escaped_v}</string>')
    lines_to_add.append("</resources>\n")
    
    new_content = content.replace("</resources>", "\n".join(lines_to_add))
    filepath.write_text(new_content, encoding="utf-8")
    # 验证是否为合法 XML
    ET.parse(filepath)
    print(f"Updated: {filepath.parent.name}")

def main() -> None:
    string_files = sorted(RES_ROOT.glob("values*/strings.xml"))
    for sf in string_files:
        dir_name = sf.parent.name
        if dir_name in ("values", "values-zh-rCN"):
            continue
        
        # 检查是否已包含
        content = sf.read_text(encoding="utf-8")
        if 'name="custom_icon"' in content:
            continue
        
        data = LANG_MAP.get(dir_name, EN_DATA)
        append_to_file(sf, data)

if __name__ == "__main__":
    main()
