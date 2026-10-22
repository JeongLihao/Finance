"""Block models and blockstates for the Finance mod's furniture-style blocks.

Usage: python tools/textures/gen_models.py [assets_finance_dir]
Default output: src/main/resources/assets/finance

Every model is authored facing north (front at z = 0); blockstates rotate it for the
other horizontal facings. Surfaces use vanilla textures wherever vanilla has one; the
detail textures come from gen_textures.py. Status lamps are a texture variable (#lamp)
that each indicator variant overrides.
"""
import json
import os
import sys

V = 'minecraft:block/'
F = 'finance:block/'

FACINGS = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
FACES = ('north', 'south', 'east', 'west', 'up', 'down')


def auto_uv(face, f, t):
    x1, y1, z1 = f
    x2, y2, z2 = t
    uv = {
        'down': [x1, 16 - z2, x2, 16 - z1],
        'up': [x1, z1, x2, z2],
        'north': [16 - x2, 16 - y2, 16 - x1, 16 - y1],
        'south': [x1, 16 - y2, x2, 16 - y1],
        'west': [z1, 16 - y2, z2, 16 - y1],
        'east': [16 - z2, 16 - y2, 16 - z1, 16 - y1],
    }[face]
    # parts poking above the block (lamps) would map outside the texture; wrap them back in
    for axis in (1, 3) if min(uv[1], uv[3]) < 0 else ():
        uv[axis] += 16
    return [round(v, 3) for v in uv]


def el(f, t, tex, faces=FACES, uv=None, rot=None, cull=False, overrides=None):
    """Element from f to t with every listed face using texture variable tex."""
    out = {'from': list(f), 'to': list(t), 'faces': {}}
    for face in faces:
        spec = {'uv': uv or auto_uv(face, f, t), 'texture': '#' + tex}
        if overrides and face in overrides:
            o = overrides[face]
            if isinstance(o, str):
                spec['texture'] = '#' + o
            else:
                spec.update(o)
        if cull and _on_boundary(face, f, t):
            spec['cullface'] = face
        out['faces'][face] = spec
    if rot:
        out['rotation'] = rot
    return out


def _on_boundary(face, f, t):
    return {'north': f[2] == 0, 'south': t[2] == 16, 'west': f[0] == 0, 'east': t[0] == 16,
            'down': f[1] == 0, 'up': t[1] == 16}[face]


def lamp(f, t):
    return el(f, t, 'lamp', uv=[4, 4, 12, 12])


def log(f, t, side, end):
    return el(f, t, side, overrides={'up': end, 'down': end})


MODELS = {}


def model(name, textures, elements, render_type=None):
    m = {'parent': 'minecraft:block/block', 'textures': textures, 'elements': elements}
    if render_type:
        m['render_type'] = render_type
    MODELS[name] = m


def variant(name, parent, **textures):
    MODELS[name] = {'parent': F + parent, 'textures': {k: v for k, v in textures.items()}}


# ---------------------------------------------------------------- terminals

def market_terminal():
    model('market_terminal', {
        'particle': V + 'polished_blackstone', 'base': V + 'smooth_stone',
        'dark': V + 'polished_blackstone', 'screen': F + 'market_screen', 'lamp': F + 'indicator_normal',
    }, [
        el((2, 0, 3), (14, 2, 13), 'base'),
        el((6, 2, 6), (10, 7, 10), 'dark'),
        el((1, 7, 6), (15, 16, 10), 'dark', overrides={'north': 'screen'}),
        lamp((6, 16, 7), (10, 17, 9)),
    ])


def securities_terminal():
    model('securities_terminal', {
        'particle': V + 'dark_oak_planks', 'wood': V + 'dark_oak_planks', 'dark': V + 'polished_blackstone',
        'screen': F + 'securities_screen', 'keys': F + 'keyboard', 'lamp': F + 'indicator_normal',
    }, [
        el((0, 8, 0), (16, 10, 16), 'wood'),
        el((1, 0, 1), (3, 8, 15), 'wood'),
        el((13, 0, 1), (15, 8, 15), 'wood'),
        el((3, 3, 13), (13, 8, 14), 'wood'),
        el((5, 10, 10), (11, 11, 13), 'dark'),
        el((2, 11, 10), (14, 16, 12), 'dark', overrides={'north': 'screen'}),
        el((3, 10, 2), (13, 11, 6), 'dark', overrides={'up': 'keys'}),
        lamp((12, 16, 10), (14, 17, 12)),
    ])


def bank_counter():
    model('bank_counter', {
        'particle': V + 'dark_oak_planks', 'wood': V + 'dark_oak_planks',
        'quartz': V + 'quartz_block_side', 'quartz_top': V + 'quartz_block_top',
        'grille': F + 'teller_grille', 'gold': V + 'gold_block', 'lamp': F + 'indicator_normal',
    }, [
        el((0, 0, 1), (16, 8, 16), 'wood', cull=True),
        el((0, 8, 0), (16, 9, 16), 'quartz', overrides={'up': 'quartz_top', 'down': 'quartz_top'}, cull=True),
        el((0, 9, 11), (16, 16, 11), 'grille', faces=('north', 'south'), uv=[0, 0, 16, 7]),
        el((0, 15, 10.5), (16, 16, 11.5), 'quartz', overrides={'up': 'quartz_top', 'down': 'quartz_top'}),
        el((3, 9, 3), (5, 11, 5), 'gold'),
        el((5, 9, 4), (7, 10, 6), 'gold'),
        lamp((7, 16, 10), (9, 17, 12)),
    ], render_type='minecraft:cutout')


def central_bank_console():
    model('central_bank_console', {
        'particle': V + 'quartz_block_side', 'quartz': V + 'quartz_block_side', 'quartz_top': V + 'quartz_block_top',
        'pillar': V + 'quartz_pillar', 'gold': V + 'gold_block', 'dark': V + 'polished_blackstone',
        'gauge': F + 'gauge_panel', 'lamp': F + 'indicator_normal',
    }, [
        el((0, 0, 0), (16, 2, 16), 'dark', cull=True),
        el((1, 2, 1), (15, 10, 15), 'pillar', overrides={'up': 'quartz_top', 'down': 'quartz_top'}),
        el((0, 10, 0), (16, 11, 16), 'gold', cull=True),
        el((0, 11, 0), (16, 12, 16), 'quartz', overrides={'up': 'quartz_top', 'down': 'quartz_top'}, cull=True),
        el((2, 12, 3), (14, 14, 13), 'dark', overrides={'up': {'texture': '#gauge', 'uv': [14, 13, 2, 3]}},
           rot={'origin': [8, 12, 8], 'axis': 'x', 'angle': -22.5}),
        lamp((6, 12, 13), (10, 14, 15)),
    ])


# ---------------------------------------------------------------- company furniture

def company_desk():
    model('company_desk', {
        'particle': V + 'dark_oak_planks', 'wood': V + 'dark_oak_planks', 'drawers': F + 'desk_drawers',
        'cover': F + 'ledger_cover', 'paper': F + 'ledger_paper',
    }, [
        el((0, 13, 0), (16, 15, 16), 'wood'),
        el((1, 0, 1), (3, 13, 15), 'wood'),
        el((9, 0, 1), (16, 13, 15), 'wood', overrides={'north': 'drawers'}),
        el((3, 4, 13), (9, 13, 14), 'wood'),
        el((2, 15, 3), (7, 16, 8), 'paper', overrides={'up': 'cover', 'down': 'cover', 'west': 'cover'}),
        el((9, 15, 4), (14, 15.25, 9), 'paper', faces=('up',)),
    ])


def boardroom_table():
    model('boardroom_table', {
        'particle': V + 'dark_oak_planks', 'wood': V + 'dark_oak_planks', 'felt': V + 'green_wool',
        'log': V + 'dark_oak_log', 'log_top': V + 'dark_oak_log_top',
    }, [
        el((3, 0, 3), (13, 1, 13), 'wood'),
        log((6, 1, 6), (10, 13, 10), 'log', 'log_top'),
        el((0, 13, 0), (16, 15, 16), 'wood', cull=True),
        el((1, 15, 1), (15, 15.5, 15), 'felt', faces=('up',)),
        el((0, 15, 0), (16, 16, 1), 'wood', cull=True),
        el((0, 15, 15), (16, 16, 16), 'wood', cull=True),
        el((0, 15, 1), (1, 16, 15), 'wood', cull=True),
        el((15, 15, 1), (16, 16, 15), 'wood', cull=True),
    ])


def company_factory_controller():
    model('company_factory_controller', {
        'particle': V + 'smooth_stone', 'side': V + 'smooth_stone', 'cap': V + 'polished_andesite',
        'panel': F + 'factory_panel', 'lamp': F + 'indicator_offline',
    }, [
        el((1, 0, 1), (15, 13, 15), 'side', overrides={'north': 'panel'}),
        el((0, 13, 0), (16, 14, 16), 'cap'),
        lamp((6, 14, 6), (10, 16, 10)),
    ])


def warehouse_controller():
    model('warehouse_controller', {
        'particle': V + 'spruce_planks', 'side': V + 'spruce_planks', 'top': V + 'barrel_top',
        'front': F + 'warehouse_front', 'lamp': F + 'indicator_normal',
    }, [
        el((0, 0, 0), (16, 16, 16), 'side', overrides={'north': 'front', 'up': 'top'}, cull=True),
        lamp((6, 16, 6), (10, 17, 10)),
    ])


def factory():
    model('factory', {
        'particle': F + 'factory_side', 'front': F + 'factory_front', 'side': F + 'factory_side',
        'top': F + 'factory_top', 'bottom': V + 'smooth_stone',
    }, [
        el((0, 0, 0), (16, 16, 16), 'side', overrides={'north': 'front', 'up': 'top', 'down': 'bottom'}, cull=True),
    ])


# ---------------------------------------------------------------- world blocks

def settlement_trade_station():
    goods = {
        'green': [
            el((2, 9, 1), (5, 12, 4), 'barrel', overrides={'up': 'barrel_top', 'down': 'barrel_top'}),
            el((8, 9, 2), (11, 12, 5), 'pumpkin', overrides={'up': 'pumpkin_top', 'down': 'pumpkin_top'}),
            el((12, 9, 2), (14, 10, 4), 'emerald'),
            el((12.5, 10, 2.5), (13.5, 11, 3.5), 'emerald'),
        ],
        'yellow': [
            el((2, 9, 1), (5, 12, 4), 'barrel', overrides={'up': 'barrel_top', 'down': 'barrel_top'}),
        ],
        'red': [],
    }
    for state, extra in goods.items():
        model('settlement_trade_station_' + state, {
            'particle': V + 'spruce_planks', 'planks': V + 'spruce_planks', 'counter': V + 'stripped_spruce_log',
            'log': V + 'spruce_log', 'log_top': V + 'spruce_log_top', 'awning': F + 'awning_' + state,
            'barrel': V + 'barrel_side', 'barrel_top': V + 'barrel_top',
            'pumpkin': V + 'pumpkin_side', 'pumpkin_top': V + 'pumpkin_top', 'emerald': V + 'emerald_block',
        }, [
            el((0, 0, 0), (16, 8, 7), 'planks', cull=True),
            el((0, 8, 0), (16, 9, 7), 'counter'),
            log((0, 0, 13), (2, 17, 15), 'log', 'log_top'),
            log((14, 0, 13), (16, 17, 15), 'log', 'log_top'),
            log((0, 9, 1), (2, 12, 3), 'log', 'log_top'),
            log((14, 9, 1), (16, 12, 3), 'log', 'log_top'),
            el((0, 15, 3), (16, 16, 16), 'awning',
               rot={'origin': [8, 15.5, 9.5], 'axis': 'x', 'angle': -22.5}),
        ] + extra)


def survey_board():
    model('survey_board', {
        'particle': V + 'spruce_planks', 'planks': V + 'spruce_planks', 'board': F + 'notice_board',
        'log': V + 'spruce_log', 'log_top': V + 'spruce_log_top',
    }, [
        log((1, 0, 7), (3, 15, 9), 'log', 'log_top'),
        log((13, 0, 7), (15, 15, 9), 'log', 'log_top'),
        el((0, 3, 7.5), (16, 14, 8.5), 'planks', overrides={'north': 'board'}),
        el((0, 14, 6), (16, 16, 10), 'planks'),
    ])


# ---------------------------------------------------------------- blockstates

TERMINAL_LAMPS = {'normal': 'normal', 'shortage': 'warning', 'offline': 'offline',
                  'restricted': 'full', 'resolution': 'risk'}
FACTORY_CONTROLLER_LAMPS = {'active': 'normal', 'missing_input': 'warning', 'output_full': 'full',
                            'risk': 'risk', 'project_pending': 'normal', 'off': 'offline'}
WAREHOUSE_LAMPS = {'normal': 'normal', 'full': 'full', 'over_capacity': 'risk', 'project_pending': 'full'}


def lamp_variants(base, mapping, default):
    names = {}
    for state, lamp_name in mapping.items():
        name = base if lamp_name == default else f'{base}_{lamp_name}'
        if name != base and name not in MODELS:
            variant(name, base, lamp=F + 'indicator_' + lamp_name)
        names[state] = name
    return names


def facing_states(models_by_key, key='indicator'):
    variants = {}
    for facing, y in FACINGS.items():
        for state, name in models_by_key.items():
            k = f'facing={facing}' + (f',{key}={state}' if state is not None else '')
            v = {'model': F + name}
            if y:
                v['y'] = y
            variants[k] = v
    return {'variants': variants}


def blockstates():
    states = {}
    for term in ('market_terminal', 'securities_terminal', 'bank_counter', 'central_bank_console'):
        states[term] = facing_states(lamp_variants(term, TERMINAL_LAMPS, 'normal'))
    states['company_factory_controller'] = facing_states(
        lamp_variants('company_factory_controller', FACTORY_CONTROLLER_LAMPS, 'offline'))
    states['warehouse_controller'] = facing_states(lamp_variants('warehouse_controller', WAREHOUSE_LAMPS, 'normal'))
    states['settlement_trade_station'] = facing_states(
        {c: 'settlement_trade_station_' + c for c in ('green', 'yellow', 'red')})
    for name in ('company_desk', 'survey_board', 'factory'):
        states[name] = facing_states({None: name})
    states['boardroom_table'] = {'variants': {'': {'model': F + 'boardroom_table'}}}
    return states


ITEMS = {
    'market_terminal': 'market_terminal', 'securities_terminal': 'securities_terminal',
    'bank_counter': 'bank_counter', 'central_bank_console': 'central_bank_console',
    'company_desk': 'company_desk', 'boardroom_table': 'boardroom_table',
    'company_factory_controller': 'company_factory_controller', 'warehouse_controller': 'warehouse_controller',
    'factory': 'factory', 'settlement_trade_station': 'settlement_trade_station_green',
    'survey_board': 'survey_board',
}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, separators=(',', ':'))
        f.write('\n')


def main():
    root = os.path.join(os.path.dirname(__file__), '..', '..')
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(root, 'src/main/resources/assets/finance')
    for build in (market_terminal, securities_terminal, bank_counter, central_bank_console, company_desk,
                  boardroom_table, company_factory_controller, warehouse_controller, factory,
                  settlement_trade_station, survey_board):
        build()
    states = blockstates()
    for name, m in MODELS.items():
        write(os.path.join(out, 'models/block', name + '.json'), m)
    for name, s in states.items():
        write(os.path.join(out, 'blockstates', name + '.json'), s)
    for item, block_model in ITEMS.items():
        write(os.path.join(out, 'models/item', item + '.json'), {'parent': F + block_model})
    print(f'wrote {len(MODELS)} block models, {len(states)} blockstates, {len(ITEMS)} item models to {out}')


if __name__ == '__main__':
    main()
