from pathlib import Path
from PIL import Image, ImageDraw

source = Path("app/build/visual-v5")
output = Path("app/build/visual-v5-previews")
output.mkdir(parents=True, exist_ok=True)
selected = ["IDLE", "WALK", "HAPPY", "HUNGRY", "TICKLE", "PULL",
            "apple", "coffee", "rocket", "plushie", "robot", "yard0", "yard1", "yard2"]

for folder in sorted(source.iterdir()):
    if not folder.is_dir():
        continue
    paths = sorted(folder.glob("*.png"))
    if not paths:
        continue
    if folder.name == "catalog":
        Image.open(paths[0]).save(output / "catalog.png")
    if folder.name in selected:
        frames = []
        for path in paths:
            with Image.open(path) as frame:
                frames.append(frame.convert("RGB").resize((720, 400)))
        frames[0].save(output / (folder.name + ".gif"), save_all=True,
                       append_images=frames[1:], duration=120, loop=0)
        mid = len(paths) // 2
        Image.open(paths[mid]).save(output / (folder.name + ".png"))
    # Readable key poses from every rendered action, including all food and toys.
    samples = [paths[int((len(paths)-1)*p)] for p in [0, .2, .4, .6, .8, 1]]
    sheet = Image.new("RGB", (1080, 630), "#f7ecd5")
    draw = ImageDraw.Draw(sheet)
    draw.text((12, 7), folder.name, fill="#443c2f")
    for index, path in enumerate(samples):
        with Image.open(path) as image:
            sheet.paste(image.convert("RGB").resize((360,200)),
                        ((index%3)*360, 30+(index//3)*300))
    sheet.save(output / (folder.name + "-poses.jpg"), quality=90)
