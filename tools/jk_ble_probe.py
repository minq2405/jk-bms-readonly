#!/usr/bin/env python3
"""
Laptop BLE Probe for JK BMS (READ-ONLY) using Windows WinRT BLE via `bleak`.
Tests scanning, dual-0xFFE1 handling, 0x97/0x96 handshake, first-time passcode caching,
live telemetry printing, and optional hex frame capture for `:bms-cli` verification.
"""

import argparse
import asyncio
import json
import struct
import sys
import time
from pathlib import Path
from typing import Optional

from bleak import BleakClient, BleakScanner
from bleak.exc import BleakError

JK_SERVICE_UUID = "0000ffe0-0000-1000-8000-00805f9b34fb"
JK_CHAR_UUID = "0000ffe1-0000-1000-8000-00805f9b34fb"
RESPONSE_HEADER = bytes([0x55, 0xAA, 0xEB, 0x90])
REQUEST_HEADER = bytes([0xAA, 0x55, 0x90, 0xEB])

CMD_CELL_INFO = 0x96
CMD_DEVICE_INFO = 0x97
FRAME_SIZE = 300
MAX_BUFFER_SIZE = 420

CACHE_PATH = Path(__file__).parent / ".jk_passcodes.json"


def crc8(data: bytes) -> int:
    return sum(data) & 0xFF


def build_read_command(cmd: int, seq: int) -> bytes:
    # Strictly READ-ONLY: only 0x96 and 0x97 are ever sent
    if cmd not in (CMD_CELL_INFO, CMD_DEVICE_INFO):
        raise ValueError(f"Blocked non-read command 0x{cmd:02X}")
    frame = bytearray(20)
    frame[0:4] = REQUEST_HEADER
    frame[4] = cmd & 0xFF
    frame[16] = seq & 0xFF
    frame[19] = crc8(frame[:19])
    return bytes(frame)


def parse_cstring(frame: bytes, offset: int, max_len: int) -> str:
    chunk = frame[offset : offset + max_len]
    nul = chunk.find(0)
    if nul >= 0:
        chunk = chunk[:nul]
    return chunk.decode("ascii", errors="ignore").strip()


def detect_variant(hw_version: str, sw_version: str) -> str:
    import re

    for s in (hw_version, sw_version):
        m = re.search(r"(\d+)", s)
        if m:
            major = int(m.group(1))
            if major <= 3:
                return "JK04"
            if major <= 10:
                return "JK02_24S"
            return "JK02_32S"
    return "JK02_32S"


def load_passcode_cache() -> dict:
    if CACHE_PATH.exists():
        try:
            return json.loads(CACHE_PATH.read_text(encoding="utf-8"))
        except Exception:
            return {}
    return {}


def save_passcode_cache(cache: dict) -> None:
    CACHE_PATH.write_text(json.dumps(cache, indent=2), encoding="utf-8")


def print_cell_summary(frame: bytes, variant: str) -> None:
    if variant == "JK04":
        cells = []
        for i in range(24):
            v = struct.unpack_from("<f", frame, 6 + i * 4)[0]
            if 0.5 < v < 5.5:
                cells.append((i + 1, v))
        total_v = sum(v for _, v in cells)
        print(f"[JK04] ActiveCells={len(cells)} SumV={total_v:.3f}V")
        return

    is_32s = variant == "JK02_32S"
    cell_offset = 16 if is_32s else 0
    tail_offset = cell_offset * 2
    max_cells = 32 if is_32s else 24

    mask = struct.unpack_from("<I", frame, 54 + cell_offset)[0]
    active_cells = []
    for i in range(max_cells):
        v = struct.unpack_from("<H", frame, 6 + i * 2)[0] * 0.001
        enabled = ((mask >> i) & 1) == 1 if mask != 0 else v > 0
        if enabled and v > 0:
            active_cells.append((i + 1, v))

    total_v = struct.unpack_from("<I", frame, 118 + tail_offset)[0] * 0.001
    current_a = struct.unpack_from("<i", frame, 126 + tail_offset)[0] * 0.001
    power_w = total_v * current_a
    t1 = struct.unpack_from("<h", frame, 130 + tail_offset)[0] * 0.1
    t2 = struct.unpack_from("<h", frame, 132 + tail_offset)[0] * 0.1
    mos_t = (
        struct.unpack_from("<h", frame, 112 + tail_offset)[0] * 0.1
        if is_32s
        else struct.unpack_from("<h", frame, 134 + tail_offset)[0] * 0.1
    )
    soc = frame[141 + tail_offset]
    rem_ah = struct.unpack_from("<I", frame, 142 + tail_offset)[0] * 0.001
    nom_ah = struct.unpack_from("<I", frame, 146 + tail_offset)[0] * 0.001
    chg_mos = frame[166 + tail_offset] != 0
    dsg_mos = frame[167 + tail_offset] != 0

    if active_cells:
        min_c = min(active_cells, key=lambda x: x[1])
        max_c = max(active_cells, key=lambda x: x[1])
        delta_mv = (max_c[1] - min_c[1]) * 1000.0
        cell_str = f"Min=C{min_c[0]}:{min_c[1]:.3f}V Max=C{max_c[0]}:{max_c[1]:.3f}V Delta={delta_mv:.1f}mV"
    else:
        cell_str = "No active cells"

    print(
        f"[{variant}] V={total_v:.3f}V | I={current_a:+.2f}A | P={power_w:+.1f}W | "
        f"SOC={soc}% ({rem_ah:.2f}/{nom_ah:.2f}Ah) | MOS={mos_t:.1f}°C T1={t1:.1f}°C T2={t2:.1f}°C | "
        f"ChgMOS={chg_mos} DsgMOS={dsg_mos} | {cell_str}"
    )


async def scan_devices(timeout: float, name_filter: Optional[str]) -> list:
    print(f"Scanning BLE devices for {timeout:.1f}s...")
    discovered = await BleakScanner.discover(timeout=timeout, return_adv=True)
    results = []
    for addr, (dev, adv) in discovered.items():
        uuids = [u.lower() for u in (adv.service_uuids or [])]
        name = dev.name or adv.local_name or "(Unknown)"
        has_ffe0 = JK_SERVICE_UUID in uuids
        if name_filter and name_filter.lower() not in name.lower() and not has_ffe0:
            continue
        results.append((addr, name, adv.rssi, has_ffe0))
    results.sort(key=lambda item: (not item[3], -(item[2] or -999)))
    return results


async def run_probe(args: argparse.Namespace) -> int:
    target_addr = args.address
    if not target_addr:
        devices = await scan_devices(args.scan_seconds, args.name)
        if not devices:
            print("No BLE devices found. Ensure Bluetooth is ON and close the phone JK BMS app.")
            return 1
        print("\nDiscovered BLE devices:")
        for idx, (addr, name, rssi, has_ffe0) in enumerate(devices):
            tag = " [JK 0xFFE0]" if has_ffe0 else ""
            print(f"  [{idx}] {addr} | {name} | RSSI={rssi}{tag}")
        if args.scan_only:
            return 0
        choice = input("\nSelect device index (Enter=0): ").strip()
        sel_idx = int(choice) if choice else 0
        target_addr = devices[sel_idx][0]

    print(f"\nConnecting to {target_addr}...")
    buffer = bytearray()
    seq = 0
    variant = args.mode
    unlocked = False
    last_cell_rx = 0.0
    captured_types = set()
    save_file = Path(args.save) if args.save else None
    if save_file:
        save_file.write_text("# Captured JK BMS frames (300B each)\n", encoding="utf-8")

    pass_cache = load_passcode_cache()
    event_device_info = asyncio.Event()
    device_info_holder = {}

    def handle_frame(frame: bytes) -> None:
        nonlocal variant, unlocked, last_cell_rx
        ftype = frame[4]
        if save_file and (ftype != 0x02 or ftype not in captured_types):
            with save_file.open("a", encoding="utf-8") as f:
                f.write(frame.hex().upper() + "\n")
            captured_types.add(ftype)

        if ftype == 0x03:
            model = parse_cstring(frame, 6, 16)
            hw = parse_cstring(frame, 22, 8)
            sw = parse_cstring(frame, 30, 8)
            dev_name = parse_cstring(frame, 46, 16)
            passcode = parse_cstring(frame, 62, 16)
            if args.mode == "AUTO":
                variant = detect_variant(hw, sw)
            device_info_holder.update(
                {"model": model, "hw": hw, "sw": sw, "name": dev_name, "passcode": passcode}
            )
            print(f"[0x03 DeviceInfo] Model={model} | HW={hw} | SW={sw} | Name={dev_name} | AutoProtocol={variant}")
            event_device_info.set()
        elif ftype == 0x01:
            if unlocked:
                print(f"[0x01 Settings] Received 300B Read-Only settings frame ({variant})")
        elif ftype == 0x02:
            last_cell_rx = time.monotonic()
            if unlocked:
                print_cell_summary(frame, variant)

    def on_notify(_sender, data: bytearray) -> None:
        if len(data) >= 4 and bytes(data[0:4]) == RESPONSE_HEADER:
            buffer.clear()
        elif len(buffer) == 0:
            return
        if len(buffer) + len(data) > MAX_BUFFER_SIZE:
            buffer.clear()
            return
        buffer.extend(data)
        if len(buffer) >= FRAME_SIZE:
            frame = bytes(buffer[:FRAME_SIZE])
            buffer.clear()
            if crc8(frame[: FRAME_SIZE - 1]) != frame[FRAME_SIZE - 1]:
                print("[WARN] CRC mismatch on incoming frame")
                return
            handle_frame(frame)

    async with BleakClient(target_addr, timeout=15.0) as client:
        jk_chars = []
        for svc in client.services:
            if svc.uuid.lower() == JK_SERVICE_UUID:
                jk_chars.extend([c for c in svc.characteristics if c.uuid.lower() == JK_CHAR_UUID])

        if not jk_chars:
            print("ERROR: Service 0xFFE0 / Characteristic 0xFFE1 not found on this device.")
            return 2

        write_char = next(
            (c for c in jk_chars if "write-without-response" in c.properties or "write" in c.properties),
            None,
        )
        notify_char = next(
            (c for c in jk_chars if "notify" in c.properties or "indicate" in c.properties),
            write_char,
        )
        if not write_char or not notify_char:
            print("ERROR: Could not resolve write/notify handles on 0xFFE1.")
            return 2

        print(f"Resolved 0xFFE1 -> write={write_char.handle}, notify={notify_char.handle}")
        await client.start_notify(notify_char, on_notify)

        await client.write_gatt_char(write_char, build_read_command(CMD_DEVICE_INFO, seq), response=False)
        seq += 1

        try:
            await asyncio.wait_for(event_device_info.wait(), timeout=6.0)
        except asyncio.TimeoutError:
            print("Timeout waiting for 0x03 Device Info frame.")
            return 3

        expected_pass = device_info_holder.get("passcode", "")
        norm_addr = target_addr.strip().upper()
        if not expected_pass:
            print("Device passcode is empty -> unlocked automatically.")
            unlocked = True
        elif pass_cache.get(norm_addr) == expected_pass:
            print(f"Passcode remembered for {norm_addr} -> auto-unlocked!")
            unlocked = True
        else:
            entered = args.passcode or input("First-time connection -> enter BMS passcode (e.g. 1234): ").strip()
            if entered == expected_pass:
                pass_cache[norm_addr] = expected_pass
                save_passcode_cache(pass_cache)
                unlocked = True
                print("Passcode verified and cached locally!")
            else:
                print("Passcode mismatch! Aborting read.")
                return 4

        await client.write_gatt_char(write_char, build_read_command(CMD_CELL_INFO, seq), response=False)
        seq += 1

        deadline = time.monotonic() + args.duration
        while time.monotonic() < deadline:
            await asyncio.sleep(1.0)
            if time.monotonic() - last_cell_rx > 1.5:
                await client.write_gatt_char(write_char, build_read_command(CMD_CELL_INFO, seq), response=False)
                seq += 1

        await client.stop_notify(notify_char)

    if save_file:
        print(f"\nSaved captured frames to: {save_file}")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Read-Only JK BMS BLE Probe (Windows Laptop)")
    parser.add_argument("--address", help="BLE MAC address (skip interactive selection)")
    parser.add_argument("--name", help="Filter scanned device names")
    parser.add_argument("--scan-only", action="store_true", help="Only list nearby BLE devices")
    parser.add_argument("--scan-seconds", type=float, default=5.0, help="Scan duration (s)")
    parser.add_argument("--mode", default="AUTO", choices=["AUTO", "JK02_32S", "JK02_24S", "JK04"])
    parser.add_argument("--passcode", help="Optional passcode for first-time unlock")
    parser.add_argument("--duration", type=float, default=15.0, help="Streaming read duration (s)")
    parser.add_argument("--save", help="Save validated 300B hex frames to file for :bms-cli")
    args = parser.parse_args()
    try:
        return asyncio.run(run_probe(args))
    except BleakError as exc:
        print(f"\n[BLE ERROR] {exc}")
        print("-> Hay kiem tra: (1) Bluetooth tren Windows da BAT chua, (2) Da tat han app JK BMS tren dien thoai chua.")
        return 1
    except KeyboardInterrupt:
        print("\nStopped by user.")
        return 0


if __name__ == "__main__":
    sys.exit(main())
