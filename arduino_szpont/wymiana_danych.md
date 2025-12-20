# Protokół wymiany danych LoRa - SZPONT

## Format komunikacji

Komunikacja odbywa się przez LoRa w formacie tekstowym. Każda wiadomość składa się z:
- **Początku** (2-3 znaki) - kod wiadomości
- **Danych** oddzielonych dwukropkami `:`
- **Końca** (2-3 znaki) - ten sam kod wiadomości (weryfikacja integralności)

Format: `KOD:param1:param2:param3:KOD`

---

## INPUT (Raspberry Pi → Aplikacja mobilna)

### BI - Boat Information
Wysyłane przy starcie lub na żądanie (GBI).

**Format:** `BI:{name}:{captain}:{mission}:BI`

**Parametry:**
- `name` - nazwa łodzi (stała: `szpont`)
- `captain` - identyfikator kapitana (stała: `1`)
- `mission` - kod misji/programu (domyślnie: `default`)

**Przykład:** `BI:szpont:1:default:BI`

---

### BIC - Boat Information Change
Wysyłane gdy zmienia się misja/program.

**Format:** `BIC:{name}:{captain}:{mission}:BIC`

**Parametry:** (jak w BI)

**Przykład:** `BIC:szpont:1:default:BIC`

---

### PA - Position Actualisation
Aktualizacja pozycji GPS.

**Format:** `PA:{lon}:{lat}:{speed}:{s_num}:PA`

**Parametry:**
- `lon` - długość geograficzna (float)
- `lat` - szerokość geograficzna (float)
- `speed` - prędkość w m/s (float)
- `s_num` - numer sekwencyjny (int)

**Przykład:** `PA:21.01234:52.12345:1.5:42:PA`

---

### SI - Sensor Information
Informacje z czujników (żyroskop, GPS, głębokość).

**Format:** `SI:{mag}:{dep}:SI`

**Parametry:**
- `mag` - dane z żyroskopu w formacie: `accel_x,accel_y,accel_z,gyro_x,gyro_y,gyro_z,mag_x,mag_y,mag_z,angle_x,angle_y,angle_z`
- `dep` - głębokość (na razie: `todo`)

**Przykład:** `SI:0.1,0.2,0.9,10.5,20.3,30.1,100,200,300,45.0,90.0,180.0:todo:SI`

**Szczegóły formatu `mag`:**
- Akcelerometr (g): `accel_x,accel_y,accel_z`
- Żyroskop (deg/s): `gyro_x,gyro_y,gyro_z`
- Magnetometr (µT): `mag_x,mag_y,mag_z`
- Kąty (deg): `angle_x,angle_y,angle_z`

---

### WI - Warning Information
Ostrzeżenie o kolizji z lidaru.

**Format:** `WI:{info_code}:WI`

**Parametry:**
- `info_code` - kod ostrzeżenia:
  - `COLLISION` - wykryto przeszkodę na kursie
  - `CLEAR` - brak przeszkód (opcjonalne)

**Przykład:** `WI:COLLISION:WI`

---

### LI - Get Lost Information
Żądanie logu z informacji po stronie socketa.

**Format:** `LI:{s_num}:LI`

**Parametry:**
- `s_num` - numer sekwencyjny żądania

**Przykład:** `LI:123:LI`

---

## OUTPUT (Aplikacja mobilna → Raspberry Pi)

### GBI - Get Boat Information
Żądanie informacji o łodzi.

**Format:** `GBI:GBI`

**Odpowiedź:** Raspberry Pi wysyła `BI:szpont:1:default:BI`

---

### SS - Set Speed
Sterowanie prędkością silników (tank drive).

**Format:** `SS:{left}:{right}:{s_num}:SS`

**Parametry:**
- `left` - wartość lewego silnika (1-10)
- `right` - wartość prawego silnika (1-10)
- `s_num` - numer sekwencyjny

**Przykład:** `SS:5:7:42:SS`

**Obsługa:** Raspberry Pi odbiera i mapuje wartości na PWM (0-100%)

---

### SA - Set Action
Akcja do wykonania (na razie ignorowana).

**Format:** `SA:{action}:{payload}:{s_num}:SA`

**Parametry:**
- `action` - kod akcji:
  - `SW` - Set NextTo Waypoint[lon;lat]
  - `SP` - Set Stop[]
  - `ST` - Set Start[]
  - `GH` - Go Home[]
  - `SM` - Set Mode[manual/waypoint]
- `payload` - dane akcji (format zależny od akcji)
- `s_num` - numer sekwencyjny

**Przykład:** `SA:SW:21.01234;52.12345:42:SA`

**Obsługa:** Na razie ignorowane.

---

### SM - Set Mission
Ustawienie misji/programu.

**Format:** `SM:{mission}:{s_num}:SM`

**Parametry:**
- `mission` - kod misji
- `s_num` - numer sekwencyjny

**Przykład:** `SM:default:42:SM`

**Obsługa:** Odbierane, ale na razie nic nie robi.

---

## Częstotliwość wysyłania

- **PA** - co 1.5 sekundy (SEND_INTERVAL_SEC)
- **SI** - co 1.5 sekundy (razem z PA lub osobno)
- **WI** - gdy wykryto kolizję (natychmiast)
- **BI/BIC** - przy starcie i zmianie misji

---

## Implementacja

Protokół jest zaimplementowany w:
- `modular/lora_motor_service.py` - wysyłanie i odbieranie przez LoRa
- `modular/sensor_service.py` - zbieranie danych z czujników
- `modular/lidar_service.py` - wykrywanie kolizji

---

## Uwagi

1. Wszystkie wartości numeryczne są wysyłane jako tekst (stringi)
2. Separator dziesiętny: kropka `.`
3. Separator pól w `mag`: przecinek `,`
4. Każda wiadomość kończy się tym samym kodem co początek (weryfikacja)
5. W przypadku błędów parsowania, wiadomość jest ignorowana

