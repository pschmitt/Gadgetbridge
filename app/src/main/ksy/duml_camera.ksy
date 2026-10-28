meta:
  id: duml_camera
  title: DUML Camera command set
  endian: le
  -duml-cmdset: 0x02

doc: |
  Commands under cmdSet=CAMERA(0x02).

types:
  file_star_flag_set_request:
    meta:
      -duml-cmd: 0xbf
    seq:
      - id: num_file_ids
        type: u1
      - id: favorite
        doc: 1 to set the Favorite flag, 0 to clear it.
        type: u1
      - id: file_ids
        type: u4
        repeat: expr
        repeat-expr: num_file_ids
      - id: request_counter
        doc: Incremented for each favorite and delete command.
        type: u4
      - id: reserved
        type: u1
      - id: num_file_ids_again
        type: u4

  file_star_flag_set_response:
    meta:
      -duml-cmd: 0xbf
    seq:
      - id: status
        type: u1
