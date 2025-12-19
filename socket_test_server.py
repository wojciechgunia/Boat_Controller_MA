#!/usr/bin/env python3
"""
Pełny serwer TCP do testowania wszystkich komend z aplikacji Boat Controller.

Obsługiwane wiadomości:
 - GBI:GBI - Get Boat Information (aplikacja → serwer)
 - BI:{name}:{captain}:{mission}:BI - Boat Information (serwer → aplikacja)
 - BIC:{name}:{captain}:{mission}:BIC - Boat Information Change (serwer → aplikacja)
 - PA:{lon}:{lat}:{speed}:{s_num}:PA - Position Actualisation (serwer → aplikacja, co sekundę)
 - SI:{mag}:{dep}:SI - Sensor Information (serwer → aplikacja, co 2 sekundy)
 - WI:{info_code}:WI - Warning Information (serwer → aplikacja, przy niskiej baterii)
 - SS:{left}:{right}:{s_num}:SS - Set Speed (aplikacja → serwer)
 - SM:{mission}:{s_num}:SM - Set Mission (aplikacja → serwer)

Uruchomienie (domyślnie port 9000):
    python socket_test_server.py

Uruchomienie na innym porcie:
    python socket_test_server.py 9100
"""

import socket
import sys
import time
import random
from datetime import datetime

DEFAULT_PORT = 9000


def log(msg: str) -> None:
    print(f"[{datetime.now().strftime('%H:%M:%S')}] {msg}")


def parse_command(line: str):
    """
    Parsuje komendę z aplikacji.
    Zwraca tuple (command_type, parsed_data) lub None przy błędzie.
    """
    line = line.strip()
    if not line:
        return None

    log(f"📥 RECV RAW: {line}")

    if line == "GBI:GBI":
        return ("GBI", None)

    if line.startswith("SS:") and line.endswith(":SS"):
        parts = line.split(":")
        if len(parts) == 5:
            try:
                left = float(parts[1])
                right = float(parts[2])
                s_num = int(parts[3])
                return ("SS", (left, right, s_num))
            except ValueError:
                log(f"⚠️  Błąd parsowania SS: {line}")
                return None

    if line.startswith("SM:") and line.endswith(":SM"):
        parts = line.split(":")
        if len(parts) == 4:
            try:
                mission = parts[1]
                s_num = int(parts[2])
                return ("SM", (mission, s_num))
            except ValueError:
                log(f"⚠️  Błąd parsowania SM: {line}")
                return None

    log(f"⚠️  Nieznana komenda: {line}")
    return None


def handle_client(conn: socket.socket, addr):
    log(f"✅ Nowe połączenie z {addr}")

    # Stan symulowanej łódki
    boat_name = "TestBoat"
    captain = "TestCaptain"
    mission = "TestMission"
    
    # Pozycja i prędkość
    lat = 52.404633  # Poznań
    lon = 16.957722
    speed = 0.0
    sequence_num = 0
    
    # Sensory
    magnetic = 45.0
    depth = 2.0
    
    # Bateria (spada co sekundę o 1%, startowo 100%)
    battery_level = 100
    
    # Timestamps dla okresowych wiadomości
    last_pa_time = time.time()
    last_si_time = time.time()
    last_battery_update = time.time()
    
    # Flaga czy wysłaliśmy już warning o niskiej baterii
    low_battery_warning_sent = False
    
    # Ustawiamy timeout, żeby móc czytać dane i wysyłać okresowe wiadomości
    conn.settimeout(0.5)
    buffer = ""

    try:
        # Wysyłamy BI zaraz po połączeniu
        bi_msg = f"BI:{boat_name}:{captain}:{mission}:BI\n"
        conn.sendall(bi_msg.encode("utf-8"))
        log(f"📤 SEND BI: name={boat_name}, captain={captain}, mission={mission}")

        while True:
            # 1. Próba odczytu danych od klienta
            try:
                data = conn.recv(1024)
                if not data:
                    break
                buffer += data.decode("utf-8")

                # Obsługa wielu linii naraz
                while "\n" in buffer:
                    line, buffer = buffer.split("\n", 1)
                    if not line:
                        continue

                    parsed = parse_command(line)
                    if parsed is None:
                        continue

                    cmd_type, cmd_data = parsed

                    if cmd_type == "GBI":
                        # Odpowiadamy BI
                        bi_msg = f"BI:{boat_name}:{captain}:{mission}:BI\n"
                        conn.sendall(bi_msg.encode("utf-8"))
                        log(f"📤 SEND BI (odpowiedź na GBI): name={boat_name}, captain={captain}, mission={mission}")

                    elif cmd_type == "SS":
                        left, right, s_num = cmd_data
                        avg_speed = (left + right) / 2.0
                        speed = avg_speed  # Aktualizujemy prędkość na podstawie SS
                        log(
                            f"✅ Otrzymano SetSpeed:"
                            f" left={left:.2f}, right={right:.2f}, s_num={s_num},"
                            f" avg_speed={avg_speed:.2f}"
                        )

                    elif cmd_type == "SM":
                        mission_name, s_num = cmd_data
                        mission = mission_name
                        log(f"✅ Otrzymano SetMission: mission='{mission}', s_num={s_num}")
                        # Wysyłamy BIC żeby poinformować o zmianie misji
                        bic_msg = f"BIC:{boat_name}:{captain}:{mission}:BIC\n"
                        try:
                            conn.sendall(bic_msg.encode("utf-8"))
                            log(f"📤 SEND BIC: name={boat_name}, captain={captain}, mission={mission}")
                        except OSError as e:
                            log(f"❌ Błąd wysyłania BIC do {addr}: {e}")
                            break

            except socket.timeout:
                # Brak danych w tym ticku – to normalne
                pass

            now = time.time()

            # 2. Co sekundę wysyłamy PA (Position Actualisation)
            if now - last_pa_time >= 1.0:
                sequence_num += 1
                
                # Symulacja ruchu łódki (jeśli speed > 0, przesuwamy się)
                if speed > 0:
                    # Przesuwamy się w losowym kierunku
                    lat += random.uniform(-0.0001, 0.0001) * speed / 10.0
                    lon += random.uniform(-0.0001, 0.0001) * speed / 10.0
                    # Ograniczenia geograficzne (żeby nie uciec za daleko)
                    lat = max(52.0, min(53.0, lat))
                    lon = max(16.0, min(18.0, lon))
                else:
                    # Gdy speed = 0, możemy delikatnie dryfować
                    lat += random.uniform(-0.00001, 0.00001)
                    lon += random.uniform(-0.00001, 0.00001)

                pa_msg = f"PA:{lon:.6f}:{lat:.6f}:{speed:.2f}:{sequence_num}:PA\n"
                try:
                    conn.sendall(pa_msg.encode("utf-8"))
                    log(f"📤 SEND PA: lon={lon:.6f}, lat={lat:.6f}, speed={speed:.2f}, s_num={sequence_num}")
                except OSError as e:
                    log(f"❌ Błąd wysyłania PA do {addr}: {e}")
                    break

                last_pa_time = now

            # 3. Co 2 sekundy wysyłamy SI (Sensor Information)
            if now - last_si_time >= 2.0:
                # Delikatnie zmieniamy wartości, bez gwałtownych skoków
                magnetic += random.uniform(-0.5, 0.5)
                magnetic = max(30.0, min(80.0, magnetic))

                depth += random.uniform(-0.1, 0.1)
                depth = max(0.5, min(10.0, depth))

                si_msg = f"SI:{magnetic:.2f}:{depth:.2f}:SI\n"
                try:
                    conn.sendall(si_msg.encode("utf-8"))
                    log(f"📤 SEND SI: mag={magnetic:.2f}, depth={depth:.2f}")
                except OSError as e:
                    log(f"❌ Błąd wysyłania SI do {addr}: {e}")
                    break

                last_si_time = now

            # 4. Co sekundę aktualizujemy baterię (spada o 1%)
            if now - last_battery_update >= 1.0:
                battery_level -= 1
                battery_level = max(0, battery_level)  # Nie spada poniżej 0
                last_battery_update = now

                # Wysyłamy warning przy 15% (tylko raz)
                if battery_level <= 15 and battery_level > 0 and not low_battery_warning_sent:
                    wi_msg = f"WI:LOW_BATTERY:WI\n"
                    try:
                        conn.sendall(wi_msg.encode("utf-8"))
                        log(f"📤 SEND WI: LOW_BATTERY (bateria={battery_level}%)")
                        low_battery_warning_sent = True
                    except OSError as e:
                        log(f"❌ Błąd wysyłania WI do {addr}: {e}")
                        break

                # Rozłączamy się gdy bateria spadnie do 0%
                if battery_level <= 0:
                    log(f"🔋 Bateria wyczerpana (0%) - rozłączanie...")
                    break

            # Małe opóźnienie, żeby nie obciążać CPU
            time.sleep(0.1)

    except Exception as e:
        log(f"❌ Błąd w obsłudze klienta {addr}: {e}")
    finally:
        conn.close()
        log(f"🔌 Rozłączono klienta {addr}")


def main():
    port = DEFAULT_PORT
    if len(sys.argv) > 1:
        try:
            port = int(sys.argv[1])
        except ValueError:
            print(f"Nieprawidłowy port: {sys.argv[1]}. Używam domyślnego {DEFAULT_PORT}")

    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)

    host = "0.0.0.0"
    srv.bind((host, port))
    srv.listen(1)

    print("=" * 60)
    print("🚢 Pełny serwer testowy Boat Controller")
    print(f"📡 Nasłuchuje na {host}:{port}")
    print(f"🌐 Dla emulatora Android użyj: 10.0.2.2:{port}")
    print(f"🖥️  Dla lokalnego testu użyj: localhost:{port}")
    print("=" * 60)
    print("Obsługiwane wiadomości:")
    print("  📥 GBI, SS, SM (od aplikacji)")
    print("  📤 BI, BIC, PA (co 1s), SI (co 2s), WI (przy baterii ≤15%)")
    print("  🔋 Bateria spada o 1% co sekundę, rozłączenie przy 0%")
    print("=" * 60)
    log("✅ Serwer uruchomiony. Oczekiwanie na połączenia...")
    print("   Naciśnij Ctrl+C aby zatrzymać serwer\n")

    try:
        while True:
            conn, addr = srv.accept()
            # Obsługujemy sekwencyjnie jednego klienta
            handle_client(conn, addr)
    except KeyboardInterrupt:
        log("🛑 Zatrzymywanie serwera...")
    finally:
        srv.close()
        log("✅ Serwer zatrzymany")


if __name__ == "__main__":
    main()

