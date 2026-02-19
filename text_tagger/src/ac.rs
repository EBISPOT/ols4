use std::collections::{HashMap, VecDeque};

/// Record separator character used to delimit fields within a single term record.
/// Each record is: label <RS> iri <RS> ontology_id
pub const RECORD_SEP: char = '\x1E';

/// Unit separator character used to delimit multiple term records stored
/// against the same key (e.g. when "cell" is a label in one ontology and
/// an exact synonym in another).
pub const UNIT_SEP: char = '\x1F';

// ============================================================================
// Builder
// ============================================================================

struct BuilderState {
    goto: HashMap<u8, u32>,
    value_idx: u32, // u32::MAX = none
}

pub struct NerAcBuilder {
    states: Vec<BuilderState>,
    /// (lowercased key bytes, value string)
    patterns: Vec<(Vec<u8>, String)>,
    entry_count: usize,
    key_set: HashMap<Vec<u8>, usize>, // dedup: lowered key -> index in `patterns`
}

impl NerAcBuilder {
    pub fn new() -> Self {
        NerAcBuilder {
            states: vec![BuilderState {
                goto: HashMap::new(),
                value_idx: u32::MAX,
            }],
            patterns: Vec::new(),
            entry_count: 0,
            key_set: HashMap::new(),
        }
    }

    /// Add `key` (matched case-insensitively) → `value`.
    /// If the key already exists, the new value is appended (separated by
    /// `UNIT_SEP`) so that a single key can resolve to multiple terms.
    pub fn add_entry(&mut self, key: &str, value: &str) {
        if key.is_empty() {
            return;
        }
        let lower: Vec<u8> = key.bytes().map(|b| b.to_ascii_lowercase()).collect();

        if let Some(&idx) = self.key_set.get(&lower) {
            self.patterns[idx].1.push(UNIT_SEP);
            self.patterns[idx].1.push_str(value);
            return;
        }

        let pat_idx = self.patterns.len();
        self.key_set.insert(lower.clone(), pat_idx);
        self.patterns.push((lower, value.to_string()));
        self.entry_count += 1;
    }

    pub fn entry_count(&self) -> usize {
        self.entry_count
    }

    /// Consume the builder and produce a flat, pointer-free `NerAc`.
    pub fn build(mut self) -> NerAc {
        // ---- Phase 1: build goto trie ----
        for pat_idx in 0..self.patterns.len() {
            let key = self.patterns[pat_idx].0.clone();
            let mut cur: u32 = 0;
            for &b in key.iter() {
                cur = match self.states[cur as usize].goto.get(&b) {
                    Some(&next) => next,
                    None => {
                        let new_id = self.states.len() as u32;
                        self.states.push(BuilderState {
                            goto: HashMap::new(),
                            value_idx: u32::MAX,
                        });
                        self.states[cur as usize].goto.insert(b, new_id);
                        new_id
                    }
                };
            }
            self.states[cur as usize].value_idx = pat_idx as u32;
        }

        let ns = self.states.len() as u32;

        // ---- Phase 2: failure + output links (BFS) ----
        let mut failure: Vec<u32> = vec![0; ns as usize];
        let mut output_link: Vec<u32> = vec![u32::MAX; ns as usize];
        let mut queue: VecDeque<u32> = VecDeque::new();

        for &child in self.states[0].goto.values() {
            failure[child as usize] = 0;
            queue.push_back(child);
        }

        while let Some(u) = queue.pop_front() {
            let children: Vec<(u8, u32)> = self.states[u as usize]
                .goto
                .iter()
                .map(|(&b, &s)| (b, s))
                .collect();
            for (b, v) in children {
                queue.push_back(v);
                let mut f = failure[u as usize];
                loop {
                    if let Some(&t) = self.states[f as usize].goto.get(&b) {
                        failure[v as usize] = t;
                        break;
                    }
                    if f == 0 {
                        failure[v as usize] = 0;
                        break;
                    }
                    f = failure[f as usize];
                }
                let fl = failure[v as usize];
                output_link[v as usize] = if self.states[fl as usize].value_idx != u32::MAX {
                    fl
                } else {
                    output_link[fl as usize]
                };
            }
        }

        // ---- Phase 3: serialise into a flat buffer ----
        //
        //  Header (20 bytes):
        //    [0]  num_states        : u32
        //    [4]  num_patterns      : u32
        //    [8]  trans_tbl_off     : u32
        //    [12] pat_lengths_off   : u32
        //    [16] values_idx_off    : u32
        //    [20] values_data_off   : u32
        //
        //  State table [HEADER .. HEADER + num_states*18]:
        //    per state (18 bytes):
        //      +0  failure      : u32
        //      +4  output_link  : u32
        //      +8  value_idx    : u32   (u32::MAX = none)
        //      +12 trans_count  : u16
        //      +14 trans_offset : u32   (record index into transition table)
        //
        //  Transition table [trans_tbl_off ..]:
        //    per transition (5 bytes, sorted by byte within each state):
        //      +0 byte   : u8
        //      +1 target : u32
        //
        //  Pattern-lengths table [pat_lengths_off ..]:
        //    per pattern (4 bytes):
        //      length : u32
        //
        //  Values index table [values_idx_off ..]:
        //    per pattern (8 bytes):
        //      data_offset : u32
        //      data_length : u32
        //
        //  Values data [values_data_off ..]:
        //    raw bytes

        const HEADER_SIZE: usize = 24;
        const STATE_REC: usize = 18;

        let np = self.patterns.len() as u32;

        // Sorted transitions per state
        let mut state_trans: Vec<Vec<(u8, u32)>> = Vec::with_capacity(ns as usize);
        for s in &self.states {
            let mut t: Vec<(u8, u32)> = s.goto.iter().map(|(&b, &id)| (b, id)).collect();
            t.sort_by_key(|&(b, _)| b);
            state_trans.push(t);
        }
        let total_trans: usize = state_trans.iter().map(|v| v.len()).sum();

        let trans_tbl_off = (HEADER_SIZE + (ns as usize) * STATE_REC) as u32;
        let pat_lengths_off = trans_tbl_off + (total_trans as u32) * 5;
        let values_idx_off = pat_lengths_off + np * 4;

        // values data
        let mut values_data: Vec<u8> = Vec::new();
        let mut values_index: Vec<(u32, u32)> = Vec::with_capacity(np as usize);
        for (_, val) in &self.patterns {
            let off = values_data.len() as u32;
            let b = val.as_bytes();
            values_data.extend_from_slice(b);
            values_index.push((off, b.len() as u32));
        }

        let values_data_off = values_idx_off + np * 8;
        let total = values_data_off as usize + values_data.len();

        let mut buf: Vec<u8> = Vec::with_capacity(total);

        // -- header --
        buf.extend(ns.to_le_bytes());
        buf.extend(np.to_le_bytes());
        buf.extend(trans_tbl_off.to_le_bytes());
        buf.extend(pat_lengths_off.to_le_bytes());
        buf.extend(values_idx_off.to_le_bytes());
        buf.extend(values_data_off.to_le_bytes());

        // -- state table --
        let mut t_off: u32 = 0;
        for i in 0..ns as usize {
            buf.extend(failure[i].to_le_bytes());
            buf.extend(output_link[i].to_le_bytes());
            buf.extend(self.states[i].value_idx.to_le_bytes());
            buf.extend((state_trans[i].len() as u16).to_le_bytes());
            buf.extend(t_off.to_le_bytes());
            t_off += state_trans[i].len() as u32;
        }

        // -- transition table --
        for tl in &state_trans {
            for &(b, tgt) in tl {
                buf.push(b);
                buf.extend(tgt.to_le_bytes());
            }
        }

        // -- pattern lengths --
        for (key, _) in &self.patterns {
            buf.extend((key.len() as u32).to_le_bytes());
        }

        // -- values index --
        for &(off, len) in &values_index {
            buf.extend(off.to_le_bytes());
            buf.extend(len.to_le_bytes());
        }

        // -- values data --
        buf.extend(&values_data);

        buf.shrink_to_fit();
        NerAc { buf }
    }
}

// ============================================================================
// Runtime: flat, pointer-free Aho-Corasick automaton
// ============================================================================

pub struct NerMatch {
    pub start: usize,
    pub end: usize,
    pub value: String,
}

pub struct NerAc {
    pub buf: Vec<u8>,
}

const HEADER_SIZE: usize = 24;
const STATE_REC: usize = 18;
const TRANS_REC: usize = 5;

impl NerAc {
    pub fn from_buf(buf: Vec<u8>) -> Self {
        NerAc { buf }
    }

    // -- header accessors --

    #[inline(always)]
    fn num_states(&self) -> u32 {
        u32::from_le_bytes(self.buf[0..4].try_into().unwrap())
    }
    #[inline(always)]
    fn num_patterns(&self) -> u32 {
        u32::from_le_bytes(self.buf[4..8].try_into().unwrap())
    }
    #[inline(always)]
    fn trans_tbl_off(&self) -> u32 {
        u32::from_le_bytes(self.buf[8..12].try_into().unwrap())
    }
    #[inline(always)]
    fn pat_lengths_off(&self) -> u32 {
        u32::from_le_bytes(self.buf[12..16].try_into().unwrap())
    }
    #[inline(always)]
    fn values_idx_off(&self) -> u32 {
        u32::from_le_bytes(self.buf[16..20].try_into().unwrap())
    }
    #[inline(always)]
    fn values_data_off(&self) -> u32 {
        u32::from_le_bytes(self.buf[20..24].try_into().unwrap())
    }

    // -- state accessors --

    #[inline(always)]
    fn state_off(s: u32) -> usize {
        HEADER_SIZE + s as usize * STATE_REC
    }
    #[inline(always)]
    fn state_failure(&self, s: u32) -> u32 {
        let o = Self::state_off(s);
        u32::from_le_bytes(self.buf[o..o + 4].try_into().unwrap())
    }
    #[inline(always)]
    fn state_output_link(&self, s: u32) -> u32 {
        let o = Self::state_off(s) + 4;
        u32::from_le_bytes(self.buf[o..o + 4].try_into().unwrap())
    }
    #[inline(always)]
    fn state_value_idx(&self, s: u32) -> u32 {
        let o = Self::state_off(s) + 8;
        u32::from_le_bytes(self.buf[o..o + 4].try_into().unwrap())
    }
    #[inline(always)]
    fn state_trans_count(&self, s: u32) -> u16 {
        let o = Self::state_off(s) + 12;
        u16::from_le_bytes(self.buf[o..o + 2].try_into().unwrap())
    }
    #[inline(always)]
    fn state_trans_offset(&self, s: u32) -> u32 {
        let o = Self::state_off(s) + 14;
        u32::from_le_bytes(self.buf[o..o + 4].try_into().unwrap())
    }

    // -- transition lookup (binary search over sorted byte keys) --

    #[inline(always)]
    fn goto(&self, state: u32, byte: u8) -> Option<u32> {
        let count = self.state_trans_count(state) as usize;
        if count == 0 {
            return None;
        }
        let base = self.trans_tbl_off() as usize
            + self.state_trans_offset(state) as usize * TRANS_REC;

        let mut lo: usize = 0;
        let mut hi: usize = count;
        while lo < hi {
            let mid = lo + (hi - lo) / 2;
            let off = base + mid * TRANS_REC;
            let b = self.buf[off];
            if b == byte {
                return Some(u32::from_le_bytes(
                    self.buf[off + 1..off + 5].try_into().unwrap(),
                ));
            } else if b < byte {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        None
    }

    // -- value / pattern-length reads --

    #[inline(always)]
    fn pattern_length(&self, pat_idx: u32) -> u32 {
        let o = self.pat_lengths_off() as usize + pat_idx as usize * 4;
        u32::from_le_bytes(self.buf[o..o + 4].try_into().unwrap())
    }

    fn read_value(&self, pat_idx: u32) -> String {
        let vi = self.values_idx_off() as usize + pat_idx as usize * 8;
        let d_off =
            u32::from_le_bytes(self.buf[vi..vi + 4].try_into().unwrap()) as usize;
        let d_len =
            u32::from_le_bytes(self.buf[vi + 4..vi + 8].try_into().unwrap()) as usize;
        let abs = self.values_data_off() as usize + d_off;
        String::from_utf8_lossy(&self.buf[abs..abs + d_len]).into_owned()
    }

    // -- emit matches walking the output-link chain --

    #[inline(always)]
    fn emit(&self, state: u32, end_pos: usize, out: &mut Vec<NerMatch>) {
        let mut s = state;
        loop {
            let vi = self.state_value_idx(s);
            if vi != u32::MAX {
                let plen = self.pattern_length(vi) as usize;
                out.push(NerMatch {
                    start: end_pos - plen,
                    end: end_pos,
                    value: self.read_value(vi),
                });
            }
            let ol = self.state_output_link(s);
            if ol == u32::MAX {
                break;
            }
            s = ol;
        }
    }

    // -- public search --

    /// Scan `text` and return all (possibly overlapping) matches.
    /// Matching is case-insensitive.
    ///
    /// When `delimiters` is `Some`, only matches whose left and right
    /// boundaries fall on a delimiter character (or the start/end of
    /// the text) are returned.
    pub fn find_all_matches(&self, text: &str, delimiters: Option<&[u8]>) -> Vec<NerMatch> {
        if self.buf.len() < HEADER_SIZE || self.num_states() == 0 {
            return Vec::new();
        }

        let mut results = Vec::new();
        let mut state: u32 = 0; // root

        for (i, raw_byte) in text.bytes().enumerate() {
            let b = raw_byte.to_ascii_lowercase();

            loop {
                if let Some(next) = self.goto(state, b) {
                    state = next;
                    break;
                }
                if state == 0 {
                    break;
                }
                state = self.state_failure(state);
            }

            // emit matches at this position
            self.emit(state, i + 1, &mut results);
        }

        // Filter by delimiter boundaries if requested
        if let Some(delims) = delimiters {
            let bytes = text.as_bytes();
            results.retain(|m| {
                let left_ok = m.start == 0 || delims.contains(&bytes[m.start - 1]);
                let right_ok = m.end >= bytes.len() || delims.contains(&bytes[m.end]);
                left_ok && right_ok
            });
        }

        results
    }
}
