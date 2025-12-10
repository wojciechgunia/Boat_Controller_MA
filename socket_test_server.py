#!/usr/bin/env python3
"""
Prosty serwer socketu TCP do testowania aplikacji Boat Controller.
Nasłuchuje na porcie 9000 i odpowiada na komendy z aplikacji.

Uruchomienie:
    python socket_test_server.py

Lub na konkretnym porcie:
    python socket_test_server.py 9000
"""

import socket
import threading
import time
import sys
from datetime import datetime

# Port domyślny
DEFAULT_PORT = 9000

# Licznik sekwencji dla PA (Position Actualisation)
sequence_counter = 0

def parse_command(data):
    """Parsuje komendę z aplikacji"""
    data = data.strip()
    print(f"[{datetime.now().strftime('%H:%M:%S')}] 📥 RECV: {data}")
    
    if data.startswith("GBI:GBI"):
        return "BI"
    elif data.startswith("SS:"):
        return "SS"
    elif data.startswith("SA:"):
        return "SA"
    elif data.startswith("SM:"):
        return "SM"
    elif data.startswith("LI:"):
        return "LI"
    else:
        return "UNKNOWN"

def send_boat_information(client_socket):
    """Wysyła BI (Boat Information)"""
    response = "BI:TestBoat:TestCaptain:TestMission:BI"
    client_socket.send((response + "\n").encode())
    print(f"[{datetime.now().strftime('%H:%M:%S')}] 📤 SEND: {response}")

def send_position_actualisation(client_socket):
    """Wysyła PA (Position Actualisation) - symuluje pozycję statku"""
    global sequence_counter
    sequence_counter += 1
    
    # Przykładowe współrzędne (okolice Poznania)
    lat = 52.404633 + (sequence_counter * 0.0001)  # Delikatnie się przesuwa
    lon = 16.957722 + (sequence_counter * 0.0001)
    speed = 2.5 + (sequence_counter % 10) * 0.1
    
    response = f"PA:{lon}:{lat}:{speed}:{sequence_counter}:PA"
    client_socket.send((response + "\n").encode())
    print(f"[{datetime.now().strftime('%H:%M:%S')}] 📤 SEND: {response}")

def send_sensor_information(client_socket):
    """Wysyła SI (Sensor Information)"""
    magnetic = 45.5 + (time.time() % 10)  # Symulacja wartości magnetycznej
    depth = 1.5 + (time.time() % 5)  # Symulacja głębokości
    
    response = f"SI:{magnetic}:{depth}:SI"
    client_socket.send((response + "\n").encode())
    print(f"[{datetime.now().strftime('%H:%M:%S')}] 📤 SEND: {response}")

def handle_client(client_socket, address):
    """Obsługuje pojedynczego klienta"""
    print(f"[{datetime.now().strftime('%H:%M:%S')}] ✅ Nowe połączenie z {address}")
    
    try:
        # Wysyłamy powitalną informację o łódce
        send_boat_information(client_socket)
        time.sleep(0.5)
        
        # Rozpoczynamy okresowe wysyłanie danych
        last_pa_time = time.time()
        last_si_time = time.time()
        
        while True:
            # Sprawdzamy czy są dane do odczytania (non-blocking)
            client_socket.settimeout(1.0)
            try:
                data = client_socket.recv(1024).decode('utf-8')
                if not data:
                    break
                
                # Parsujemy i odpowiadamy na komendy
                cmd = parse_command(data)
                
                if cmd == "BI":
                    send_boat_information(client_socket)
                elif cmd == "SS":
                    # Set Speed - potwierdzamy odbiór
                    print(f"[{datetime.now().strftime('%H:%M:%S')}] ✅ Otrzymano SetSpeed")
                    # Możemy wysłać potwierdzenie przez PA z nową prędkością
                elif cmd == "SA":
                    # Set Action - parsujemy akcję
                    parts = data.split(":")
                    if len(parts) >= 2:
                        action = parts[1]
                        print(f"[{datetime.now().strftime('%H:%M:%S')}] ✅ Otrzymano SetAction: {action}")
                        if action == "ST":
                            print(f"[{datetime.now().strftime('%H:%M:%S')}] 🚢 Start misji")
                        elif action == "SP":
                            print(f"[{datetime.now().strftime('%H:%M:%S')}] 🛑 Stop misji")
                        elif action == "GH":
                            print(f"[{datetime.now().strftime('%H:%M:%S')}] 🏠 Go Home")
                elif cmd == "SM":
                    # Set Mission - parsujemy misję
                    parts = data.split(":")
                    if len(parts) >= 2:
                        mission = parts[1]
                        print(f"[{datetime.now().strftime('%H:%M:%S')}] ✅ Otrzymano SetMission: {mission}")
                elif cmd == "LI":
                    # Lost Information - wysyłamy ostatnie PA
                    print(f"[{datetime.now().strftime('%H:%M:%S')}] 📋 Żądanie Lost Information")
                    send_position_actualisation(client_socket)
                    
            except socket.timeout:
                # Timeout - to normalne, kontynuujemy wysyłanie okresowych danych
                pass
            
            # Wysyłamy PA co 2 sekundy
            current_time = time.time()
            if current_time - last_pa_time >= 2.0:
                send_position_actualisation(client_socket)
                last_pa_time = current_time
            
            # Wysyłamy SI co 3 sekundy
            if current_time - last_si_time >= 3.0:
                send_sensor_information(client_socket)
                last_si_time = current_time
                
    except Exception as e:
        print(f"[{datetime.now().strftime('%H:%M:%S')}] ❌ Błąd w obsłudze klienta {address}: {e}")
    finally:
        client_socket.close()
        print(f"[{datetime.now().strftime('%H:%M:%S')}] 🔌 Rozłączono klienta {address}")

def main():
    port = DEFAULT_PORT
    if len(sys.argv) > 1:
        try:
            port = int(sys.argv[1])
        except ValueError:
            print(f"Nieprawidłowy port: {sys.argv[1]}. Używam domyślnego {DEFAULT_PORT}")
    
    # Tworzymy socket
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    
    # Nasłuchujemy na wszystkich interfejsach (0.0.0.0)
    host = '0.0.0.0'
    
    try:
        server_socket.bind((host, port))
        server_socket.listen(5)
        print("=" * 60)
        print(f"🚢 Serwer socketu Boat Controller")
        print(f"📡 Nasłuchuje na {host}:{port}")
        print(f"🌐 Dla emulatora Android użyj: 10.0.2.2:{port}")
        print(f"🖥️  Dla lokalnego testu użyj: localhost:{port}")
        print("=" * 60)
        print(f"[{datetime.now().strftime('%H:%M:%S')}] ✅ Serwer uruchomiony. Oczekiwanie na połączenia...")
        print("   Naciśnij Ctrl+C aby zatrzymać serwer\n")
        
        while True:
            client_socket, address = server_socket.accept()
            # Każde połączenie obsługujemy w osobnym wątku
            client_thread = threading.Thread(
                target=handle_client,
                args=(client_socket, address),
                daemon=True
            )
            client_thread.start()
            
    except KeyboardInterrupt:
        print(f"\n[{datetime.now().strftime('%H:%M:%S')}] 🛑 Zatrzymywanie serwera...")
    except Exception as e:
        print(f"[{datetime.now().strftime('%H:%M:%S')}] ❌ Błąd serwera: {e}")
    finally:
        server_socket.close()
        print(f"[{datetime.now().strftime('%H:%M:%S')}] ✅ Serwer zatrzymany")

if __name__ == "__main__":
    main()

