# Przekaźnik SMS (Android / Samsung)

Aplikacja nasłuchuje przychodzących SMS-ów i – gdy pasują do reguły (nadawca i/lub słowa w treści) –
przekazuje je SMS-em na wskazane numery. Działa w pełni w tle, także przy zablokowanym ekranie.

## 1. Zbudowanie pliku APK

**Wariant A – bez instalowania czegokolwiek (GitHub, ~5 min)**
1. Załóż konto na github.com → „New repository” (może być prywatne).
2. „uploading an existing file” → przeciągnij **całą zawartość** rozpakowanego folderu (łącznie z ukrytym `.github`).
   Jeśli przeglądarka pominie folder `.github`, utwórz w repo plik `.github/workflows/build.yml` i wklej jego treść.
3. Zakładka **Actions** → „Zbuduj APK” → po zakończeniu (zielony ✓) pobierz artefakt **PrzekaznikSMS-apk**.
4. W środku jest `app-release.apk`.

**Wariant B – Android Studio:** File → Open → wskaż folder → Build → Build APK(s).

## 2. Instalacja na Samsungu
1. Skopiuj APK na telefon i otwórz go (zezwól na instalację z tego źródła). Jeśli Play Protect ostrzega → „Zainstaluj mimo to”.
2. Uruchom aplikację i w karcie **Gotowość** doprowadź wszystko do ✅:
   - uprawnienia SMS,
   - brak ograniczeń baterii + Ustawienia → Bateria → Limity użycia w tle → *Aplikacje nigdy nieusypiane* → dodaj aplikację
     (inaczej Samsung może po jakimś czasie ubić aplikację w tle i przestanie ona reagować na SMS-y).

## 3. Reguły
- **Nadawcy**: numery lub nazwy (np. `600123456, ORLEN`). Numery porównywane po 9 ostatnich cyfrach.
- **Słowa kluczowe**: `zlecenie, awaria` – wystarczy jedno (lub wszystkie, jeśli zaznaczysz).
- Nadawca **i** słowa muszą być spełnione jednocześnie; puste pole = dowolne.
- **Odbiorcy**: numery, na które SMS ma zostać przekazany.
- **Szablon**: `{nadawca} {tresc} {czas} {regula}`.
- Przycisk **Test wysyłki** w regule wysyła próbną wiadomość do wszystkich odbiorców.
- Ochrona przed pętlą: jeśli numer odbiorcy jest jednocześnie nadawcą, ten SMS nie zostanie do niego odesłany.

## 4. Uwagi
- Długi SMS przekazywany dalej może zostać podzielony na kilka wiadomości (SMS ma limit 160 znaków,
  a polskie znaki/emoji zmniejszają go do 70).
- Dual SIM: SMS-y wychodzą z karty ustawionej jako domyślna do wiadomości.
- Wysyłka do wielu odbiorców w jednej regule oznacza tyle SMS-ów, ile jest odbiorców – może to generować koszty
  jak przy zwykłym wysyłaniu SMS-ów z telefonu.

## 5. Struktura kodu
- `Forwarder.kt` – odbiór SMS, dopasowanie reguł, wysyłka SMS.
- `Store.kt` – reguły i dziennik (pamięć telefonu).
- `Screens.kt` – ekrany aplikacji.
