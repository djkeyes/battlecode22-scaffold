
import os
import platform
import subprocess
import shutil
import itertools
import yaml
from collections import deque

import tqdm
import numpy as np

src_dir = './src/'
checkout_dir = './benchmarker/scratch/'
build_dir = checkout_dir + 'build/'

# benchmarks, in the format (package name, commit hash or branch, debug params)
# (use a singleton list if no params to try)
reference_benchmarks = [
    ('examplefuncsplayer', 'a6f9f20eaedccd8c2b117a8057d3b28f93e8774b', ['']),
    ('simpleplayer', 'cc2802ceabebcd97895b5341bc2bc16bba1eab74', ['']),
    #('simpleplayer', '3371a0920668c285825598d73d18e08c918be5b4', ['']), # revamped worker assignment
    #('simpleplayer', 'cd72ddb393348003d6dbc00ea73dbf82e8b3aa46', ['']), # fewer builders
    #('simpleplayer', '1bd87f22d967d0ef31f60a717a44876143127a46', ['']), # new pathfinding
    #('simpleplayer', '5ce389b2fb4f61fcc60282d2879a1ec5270c89b5', ['']), # tuned for certain cramped maps
    ('simpleplayer', 'a63d8992d08e87a9e18c1e10e0b3a4f0f4a1b1a7', ['']), # improved combat
]

latest_bots = [
    ('simpleplayer', ['']),
]

maps = [
    # Leadless maps
    'BigEmpty',
    'MediumEmpty',
    'SmallEmpty',

    # small maps
    'InverseGradient',
    '10x10',
    'Gradient',
    'Random',
    'Checkers',
    #'Checkers2',
    'SuddenDeath',

    # close archon start maps
    'Pods',
    'Gladiators',

    # medium maps
    'BigMoney',
    'Hawaii',
    'KingOfTheHill',

    # big maps
    'Mushroom',
    'Hermits',
    'ARiverRunsThroughIt',
    'Cave',
    'Corridor',
    'LeadIsHere',
    'Squiggles',
    'Turtle',
    'Desert',

    # gimicky maps
    #'AllRubble',
    #'XenosArrow',
    #'HardToPathfind',

    # Official maps
    'maptestsmall',
    'colosseum',
    'eckleburg',
    'fortress',
    'intersection',
    'jellyfish',
    'maptestsmall',
    'nottestsmall',
    'progress',
    'rivers',
    'sandwich',
    'squer',
    'uncomfortable',
    'underground',
    'valley',

    # Troll maps
    #'TrollApocalypse',
    'TrollOverflow',
    'TrollBig',
    #'TrollCheckers',
]

benchmark_prefix = 'benchmark'

benchmark_historical = False # compare all reference benchmarks
benchmark_latest = True # compare the latest bots to reference

runs_per_matchup = 2 # 100 or so would be better

###########################################

benchmark_prefix = 'benchmark'

num_procs = 24

WAIT_SECONDS_PER_PROCESS = 1.0

###########################################


def get_gradle_command():
    if platform.system() == 'Windows':
        return 'gradlew.bat'
    else:
        return './gradlew'

def create_match_command(team_a, team_b, param_a, param_b, map_name, seed_a, seed_b):
    gradle_command = get_gradle_command()
    param_a = ''.join(param_a)
    param_b = ''.join(param_b)
    command = [gradle_command, 'fastrun',
        '-PteamA=' + team_a,
        '-PteamB=' + team_b,
        '-PparamA=' + param_a,
        '-PparamB=' + param_b,
        '-Pmaps=' + map_name,
        '-PseedA=' + str(seed_a),
        '-PseedB=' + str(seed_b),
        '-Psource=' + checkout_dir,
        '-PbuildDir=' + build_dir
    ]
    return command

def clear_scratch():
    if os.path.exists(checkout_dir):
        shutil.rmtree(checkout_dir)
    os.makedirs(checkout_dir)

def checkout_benchmarks():
    # flatten and checkout
    flattened_benchmarks = []
    already_copied = set()
    for benchmark in reference_benchmarks:
        orig_package, commit_identifier, params = benchmark

        package_path = os.path.join(src_dir, orig_package)

        generated_package = f'{benchmark_prefix}_{orig_package}_{commit_identifier}'
        generated_package_path = os.path.join(checkout_dir, generated_package)

        if generated_package not in already_copied:
            already_copied.add(generated_package)

            subprocess.run(['git', f'--work-tree={checkout_dir}', 'checkout', commit_identifier, '--', package_path])
            shutil.move(os.path.join(checkout_dir, package_path), generated_package_path)
            # for each file, pipe it to sed to replace the package name, then write it to a new directory
            for filename in os.listdir(generated_package_path):
                full_filename = os.path.join(generated_package_path, filename)
                pipe = subprocess.Popen(['cat', full_filename], stdout=subprocess.PIPE)
                pipe.wait()
                with open(full_filename, 'w') as f:
                    # TODO(daniel): this could probably be done with shutil, which would help to make this script run on windows
                    package_regex = 's/package ' + orig_package + '/package ' + generated_package + '/'
                    static_import_regex = 's/import static ' + orig_package + '/import static ' + generated_package + '/'
                    command = ['sed', '-r', '; '.join([package_regex, static_import_regex])]
                    sed = subprocess.Popen(command, stdin=pipe.stdout, stdout=f)
                    pipe.wait()

        flattened_benchmarks.append((generated_package, params))
    return flattened_benchmarks

def copy_latest(latest_bots):
    # just directly copy this--git doesn't know it exists yet.
    already_copied = set()
    for latest in latest_bots:
        orig_package, params = latest

        if orig_package not in already_copied:
            already_copied.add(orig_package)

            package_path = os.path.join(src_dir, orig_package)

            generated_package = f'{benchmark_prefix}_{orig_package}'
            generated_package_path = os.path.join(checkout_dir, generated_package)

            shutil.copytree(package_path, generated_package_path)

def build_bots():
    gradle_command = get_gradle_command()
    command = [gradle_command, 'build', '-Psource=' + checkout_dir, '-PbuildDir=' + build_dir]
    subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

def get_match_winner(match_out):
    winsIdx = match_out.find(b'wins')
    prevNewlineIdx = match_out.rfind(b'\n', 0, winsIdx)
    nextNewlineIdx = match_out.find(b'\n', winsIdx)
    winString = match_out[prevNewlineIdx:nextNewlineIdx].decode('utf-8')
    tokens = winString.split()
    winner = tokens[2][1]
    if winner == 'A':
        return 0
    else:
        return 1

def benchmark_round_robin(flattened_benchmarks):
    # TODO: might want to track win conditions and time to victory
    # a tiebreaker win at turn 2000 is much less convincing than a win at turn 100

    num_wins = np.zeros((len(flattened_benchmarks), len(flattened_benchmarks), len(maps)))
    num_games = np.zeros_like(num_wins)
    iteration = 0
    matchups = list(itertools.permutations(range(len(flattened_benchmarks)), r=2))
    total_iterations = len(matchups) * len(maps) * runs_per_matchup
    print(f'running matches ({total_iterations} matches total)...')
    progress_bar = tqdm.tqdm(total=total_iterations)
    progress_bar.update(iteration)
    match_commands = deque()
    for i, j in matchups:
        for s, map in enumerate(maps):
            team_a, param_a = flattened_benchmarks[i]
            team_b, param_b = flattened_benchmarks[j]
            for k in range(runs_per_matchup):
                seed_a = 2*(k * len(maps) + s)
                seed_b = seed_a + 1

                command = create_match_command(team_a, team_b, param_a, param_b, map, seed_a, seed_b)
                matchup_args = (i, j, s)
                match_commands.append((matchup_args, command))
    async_match_commands = deque()
    for _ in range(min(num_procs, len(match_commands))):
        (matchup_args, command) = match_commands.popleft()
        async_match_commands.append((matchup_args, subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE)))
    while len(async_match_commands) > 0:
        (matchup_args, process) = async_match_commands.popleft()
        (i, j, s) = matchup_args
        try:
            process.wait(WAIT_SECONDS_PER_PROCESS)
        except:
            # TODO: use the asyncio api, and handle processes as soon as they finish
            # Typically, most matches conclude quickly. However, there is a long tail--stalemate games typically take a
            # long time to conclude. So move it to the end of the queue, and process other things in the meantime.
            async_match_commands.append((matchup_args, process))
            progress_bar.update(0)
            continue
        stdout, stderr = process.communicate()

        if len(stderr) > 0:
            print('\nEncountered a long error message. is the process running correctly? to reproduce, run:')
            print(" ".join(command))
            print()
            print(stderr)

        winner = get_match_winner(stdout)
        if winner == 0:
            num_wins[i, j, s] += 1
        else:
            num_wins[j, i, s] += 1
        num_games[i, j, s] += 1
        num_games[j, i, s] += 1

        process.stdout.close()
        process.stderr.close()
        process.__exit__(None, None, None)
        del process

        iteration += 1
        progress_bar.update(1)

        if len(match_commands) > 0:
            (matchup_args, command) = match_commands.popleft()
            async_match_commands.append((matchup_args, subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE)))


    for i in range(len(flattened_benchmarks)):
        for s in range(len(maps)):
            num_games[i, i, s] = 1
    win_rate = num_wins / num_games

    print('\n\n', flush=True)

    # these names are long and ugly, print them first
    print('\tBENCHMARK ID MAP')
    print('Id\tbenchmark param tuple')
    for i, benchmark in enumerate(flattened_benchmarks):
        print('{}\t{}'.format(i, benchmark))

    for s in range(len(maps)):
        print()
        print('-------------------------------------------------------------------------------------')
        print('-------------------------------------------------------------------------------------')
        print()
        map = maps[s]
        print(f'\t\tROUND ROBIN WIN RATES PER MAP, map name={map}')
        print('\t\t\topponent Id')
        print('\t\t', end='')
        for j in range(len(flattened_benchmarks)):
            print(f'\t  {j}', end='')
        print('\toverall')

        for i in range(len(flattened_benchmarks)):
            print(f'\t\t  {i}', end='')
            for j in range(len(flattened_benchmarks)):
                print('\t{:.4f}'.format(win_rate[i, j, s]), end='')
            mean_rate = np.sum(win_rate[i, :, s]) / (win_rate.shape[1] - 1)
            print('\t{:.4f}'.format(np.mean(mean_rate)))

    print()
    print('-------------------------------------------------------------------------------------')
    print()

    print('\tOVERALL ROUND ROBIN WIN RATES')

    print('\t\topponent Id')
    print('\t', end='')
    for j in range(len(flattened_benchmarks)):
        print(f'\t  {j}', end='')
    print('\toverall')

    for i in range(len(flattened_benchmarks)):
        print(f'\t  {i}', end='')
        for j in range(len(flattened_benchmarks)):
            print('\t{:.4f}'.format(np.mean(win_rate[i, j, :])), end='')
        overall_mean = np.sum(win_rate[i, :, :]) / (win_rate.shape[1] - 1) / win_rate.shape[2]
        print('\t{:.4f}'.format(overall_mean))



def benchmark_latest_bots(latest_bots, flattened_reference_benchmarks):

    num_wins = np.zeros((len(latest_bots), len(flattened_reference_benchmarks), len(maps)))
    num_games = np.zeros_like(num_wins)
    iteration = 0

    matchups = list(itertools.product(range(len(latest_bots)), range(len(flattened_reference_benchmarks))))
    total_iterations = len(matchups) * len(maps) * runs_per_matchup * 2
    print(f'running matches ({total_iterations} matches total)...')
    progress_bar = tqdm.tqdm(total=total_iterations)
    progress_bar.update(iteration)
    match_commands = deque()
    for i, j in matchups:
        for s, map in enumerate(maps):
            for reverse in [False, True]:
                team_a, param_a = latest_bots[i]
                team_b, param_b = flattened_reference_benchmarks[j]
                if reverse:
                    team_a, param_a, team_b, param_b = team_b, param_b, team_a, param_a
                for k in range(runs_per_matchup):
                    seed_a = 2*(k * len(maps) + s)
                    seed_b = seed_a + 1
                    command = create_match_command(team_a, team_b, param_a, param_b, map, seed_a, seed_b)
                    matchup_args = (i, j, s, reverse)
                    match_commands.append((matchup_args, command))
    async_match_commands = deque()
    for _ in range(min(num_procs, len(match_commands))):
        (matchup_args, command) = match_commands.popleft()
        async_match_commands.append((matchup_args, subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE)))
    while len(async_match_commands) > 0:
        (matchup_args, process) = async_match_commands.popleft()
        (i, j, s, reverse) = matchup_args
        try:
            process.wait(WAIT_SECONDS_PER_PROCESS)
        except:
            # TODO: use the asyncio api, and handle processes as soon as they finish
            # Typically, most matches conclude quickly. However, there is a long tail--stalemate games typically take a
            # long time to conclude. So move it to the end of the queue, and process other things in the meantime.
            async_match_commands.append((matchup_args, process))
            progress_bar.update(0)
            continue
        stdout, stderr = process.communicate()

        if len(stderr) > 0:
            print('\nEncountered a long error message. is the process running correctly? to reproduce, run:')
            print(" ".join(command))
            print()
            print(stderr)

        winner = get_match_winner(stdout)
        if reverse:
            winner = 1 - winner
        if winner == 0:
            num_wins[i, j, s] += 1
        num_games[i, j, s] += 1

        process.stdout.close()
        process.stderr.close()
        process.__exit__(None, None, None)
        del process

        iteration += 1
        progress_bar.update(1)

        if len(match_commands) > 0:
            (matchup_args, command) = match_commands.popleft()
            async_match_commands.append((matchup_args, subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE)))

    win_rate = num_wins / num_games

    print('\n\n', flush=True)

    # these names are long and ugly, print them first
    print('\tBENCHMARK ID MAP')
    print('Id\tbenchmark param tuple')
    for i, benchmark in enumerate(flattened_reference_benchmarks):
        print('{}\t{}'.format(i, benchmark))

    print()

    print('\tBOT ID MAP')
    print('Id\tbot name in working directory')
    for i, bot in enumerate(latest_bots):
        print('{}\t{}'.format(i, bot))

    for s in range(len(maps)):
        print()
        print('-------------------------------------------------------------------------------------')
        print('-------------------------------------------------------------------------------------')
        print()
        map = maps[s]
        print(f'\t\tVS REFERENCE WIN RATES PER MAP, map name={map}')
        print('\t\t\treference Id')
        print('\t\t', end='')
        for j in range(len(flattened_reference_benchmarks)):
            print(f'\t  {j}', end='')
        print('\toverall')

        for i in range(len(latest_bots)):
            print(f'\t\t  {i}', end='')
            for j in range(len(flattened_reference_benchmarks)):
                print('\t{:.4f}'.format(win_rate[i, j, s]), end='')
            mean_rate = np.mean(win_rate[i, :, s])
            print('\t{:.4f}'.format(np.mean(mean_rate)))

    print()
    print('-------------------------------------------------------------------------------------')
    print()

    print('\tOVERALL VS REFERENCE WIN RATES')

    print('\t\treference Id')
    print('\t', end='')
    for j in range(len(flattened_reference_benchmarks)):
        print(f'\t  {j}', end='')
    print('\toverall')

    for i in range(len(latest_bots)):
        print(f'\t  {i}', end='')
        for j in range(len(flattened_reference_benchmarks)):
            print('\t{:.4f}'.format(np.mean(win_rate[i, j, :])), end='')
        overall_mean = np.mean(win_rate[i, :, :])
        print('\t{:.4f}'.format(overall_mean))

def main():

    print('checking out reference players...')

    clear_scratch()
    flattened_reference_benchmarks = checkout_benchmarks()
    copy_latest(latest_bots)
    build_bots()
    if benchmark_historical:
        print('performing round robin benchmarks...')
        benchmark_round_robin(flattened_reference_benchmarks)

    if benchmark_latest:
        if len(latest_bots) > 1:
            print('performing round robin of latest code...')
            benchmark_round_robin(latest_bots)
        print('comparing latest code to reference benchmarks...')
        benchmark_latest_bots(latest_bots, flattened_reference_benchmarks)


if __name__=="__main__":
    main()
