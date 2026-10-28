meta:
  id: duml_battery
  title: DUML Battery command set
  endian: le
  -duml-cmdset: 0x0d

doc: |
  Commands under cmdSet=BATTERY(0x0d).

types:
  dynamic_data:
    meta:
      -duml-cmd: 0x02
    seq:
      - id: result
        type: u1
      - id: voltage
        doc: Millivolts.
        type: u4
      - id: current
        doc: Milliamperes. Positive while charging, negative while discharging.
        type: s4
      - id: full_capacity
        type: u4
      - id: remaining_capacity
        type: u4
      - id: temperature
        doc: Tenths of a degree Celsius.
        type: u2
      - id: cell_count
        type: u1
      - id: state_of_charge
        doc: Percent.
        type: u1
      - id: status
        type: u8
