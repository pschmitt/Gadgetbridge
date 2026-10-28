meta:
  id: duml_general
  title: DUML General command set
  endian: le
  -duml-cmdset: 0x00

doc: |
  Commands under cmdSet=GENERAL(0x00).

types:
  file_list_request:
    doc: |
      Requests one page of the file list.
    seq:
      - id: magic
        doc: Always 0x4a.
        type: u1
      - id: msg_type
        doc: 0x00 for a list request.
        type: u1
      - id: length_flags
        doc: Bits 0-11 are the payload length. Bits 12-15 are 1.
        type: u2
      - id: transfer_id
        type: u2
      - id: fragment_index
        type: u4
      - id: cursor
        doc: 1 for the newest file, or the File Id of the last record of the previous Page.
        type: u4
      - id: max_records
        type: u2
      - id: unknown1
        doc: Meaning unknown. The app sends 0x010d.
        type: u2
      - id: favorites_only
        type: u1
      - id: video_filter
        doc: 0xffffffff for no filter, 0x80000000 to include videos, 0 to exclude them.
        type: u4
      - id: photo_filter
        doc: 0xffffffff for no filter, 0x80000000 to include photos, 0 to exclude them.
        type: u4
      - id: unknown2
        doc: Meaning unknown. The app sends 0x0100.
        type: u2
      - id: reserved
        size: 13

  file_transfer_ack:
    doc: |
      The 0x26 payload that confirms the end of a transfer.
    seq:
      - id: magic
        doc: Always 0x4a.
        type: u1
      - id: msg_type
        doc: 0x04 for a transfer ack.
        type: u1
      - id: length_flags
        doc: Bits 0-11 are the payload length. Bits 12-15 are 1.
        type: u2
      - id: transfer_id
        type: u2
      - id: fragment_index
        type: u4
      - id: status
        doc: The app sends 1.
        type: u4

  file_transfer_response:
    meta:
      -duml-cmd: 0x26
    seq:
      - id: status
        type: u1

  file_transfer_data_request:
    meta:
      -duml-cmd: 0x27
    doc: |
      One message of a file list transfer from the camera.
    seq:
      - id: magic
        doc: Always 0x4a.
        type: u1
      - id: msg_type
        doc: 0x01 for a data fragment, 0x03 for the end of the transfer.
        type: u1
      - id: length_flags
        type: u2
      - id: transfer_id
        type: u2
      - id: fragment_index
        type: u4
      - id: data
        size-eos: true

  file_transfer_delete_request:
    meta:
      -duml-cmd: 0x28
    seq:
      - id: num_file_ids
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

  file_transfer_delete_response:
    meta:
      -duml-cmd: 0x28
    seq:
      - id: status
        type: u1
