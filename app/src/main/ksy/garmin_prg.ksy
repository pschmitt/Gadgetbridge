meta:
  id: garmin_prg
  file-extension: prg
  endian: be

seq:
  - id: sections
    type: section
    repeat: until
    repeat-until: _.magic == section_magic::end or _io.eof

types:
  section:
    seq:
      - id: magic
        type: u4
        enum: section_magic
      - id: length
        type: u4
      - id: content
        size: length
        type:
          switch-on: magic
          cases:
            section_magic::entry_points: section_entry_points

  section_entry_points:
    seq:
      - id: num_entry_points
        type: u2
      - id: entry_points
        type: entry_point
        repeat: expr
        repeat-expr: num_entry_points

  entry_point:
    seq:
      - id: uuid
        size: 16
      - id: module_id
        type: u4
      - id: class_id
        type: u4
      - id: label_id
        type: u4
      - id: icon_label_id
        type: u4
      - id: app_type
        type: u4
        enum: app_type

enums:
  section_magic:
    0x00000000: end
    0xd000d000: head
    0xd000d00d: head_versioned
    0xc0debabe: code
    0xc0de10ad: code_extended
    0xda7ababe: data
    0xc0de7ab1: pc_to_line_num
    0x6060c0de: entry_points
    0xc1a557b1: link_table
    0x6000db01: permissions
    0x0ece7105: exceptions
    0x5717b015: symbols
    0xbaada555: string_resource_symbols
    0x5e771465: settings
    0xd011aaa5: app_unlock
    0xf00d600d: resource
    0xdefeca7e: background_resource
    0xd00dface: glance_resource
    0x00005161: app_store_signature
    0xe1c0de12: developer_signature
    0xd0000d1e: debug
    0xfaceda7a: complication
  app_type:
    0: watch_face
    1: watch_app
    2: datafield
    3: widget
    4: background
    5: audio_content_provider_app
    6: glance
