package tk.darrow.chocobosreborn.client;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.PrintWriter;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

class ModelSurfaceTest {
	private static final String[] VARIANTS = {"chocobo", "chocobo_saddled", "chocobo_armor_iron", "chocobo_armor_diamond"};

	@Test
	void tailRootDoesNotStretchIntoSpikesDuringRun() throws Exception {
		for (String variant : VARIANTS) {
			WhiskerMesh mesh = WhiskerMesh.parse(Files.readAllBytes(Path.of("src/main/resources/assets/chocobosreborn/entity", variant + ".ncgb")));
			WhiskerMesh.Part part = mesh.parts[0];
			WhiskerMesh.Clip clip = mesh.clipByName.get("run");
			MeshSkinner skinner = new MeshSkinner();
			float[] frame = new float[clip.bones * 12];
			int checked = 0;
			for (int f = 0; f < clip.frames; f++) {
				System.arraycopy(clip.m, f * frame.length, frame, 0, frame.length);
				skinner.composeRows(frame, clip.bones, new float[]{1,0,0,0,0,1,0,0,0,0,1,0}, new float[]{1,0,0,0,1,0,0,0,1});
				skinner.skin(part, new boolean[clip.bones], false);
				for (int t = 0; t < part.tri.length; t += 3) {
					for (int edge = 0; edge < 3; edge++) {
						int a = part.tri[t + edge], b = part.tri[t + (edge + 1) % 3];
						float x = (part.pos[a * 3] + part.pos[b * 3]) / 2;
						float y = (part.pos[a * 3 + 1] + part.pos[b * 3 + 1]) / 2;
						float z = (part.pos[a * 3 + 2] + part.pos[b * 3 + 2]) / 2;
						if (Math.abs(x) >= .32F || y <= 1 || y >= 1.6F || z >= -.35F) continue;
						double rest = distance(part.pos, a * 3, b * 3);
						if (rest <= .005) continue;
						double posed = distance(skinner.pos, part.posIndex[a] * 3, part.posIndex[b] * 3);
						assertTrue(posed / rest < 1.5, variant + " tail spike, frame " + f + ", stretch " + posed / rest);
						checked++;
					}
				}
			}
			assertTrue(checked > 1000);
		}
	}

	private static double distance(float[] xyz, int a, int b) {
		double x = xyz[a] - xyz[b], y = xyz[a + 1] - xyz[b + 1], z = xyz[a + 2] - xyz[b + 2];
		return Math.sqrt(x * x + y * y + z * z);
	}

	@Test
	void femaleTuftKeepsEveryFaceAndDoesNotChangeTheBody() throws Exception {
		for (String variant : VARIANTS) {
			WhiskerMesh mesh = WhiskerMesh.parse(Files.readAllBytes(Path.of("src/main/resources/assets/chocobosreborn/entity", variant + ".ncgb")));
			for (int level = 0; level <= WhiskerMesh.lodLevels(); level++) {
				WhiskerMesh.Part[] male = mesh.parts(level, true), female = mesh.parts(level, false);
				assertSame(female, mesh.parts(level, false), "cache the variant outside the render loop");
				for (int part = 0; part < male.length; part++) {
					WhiskerMesh.Part m = male[part], f = female[part];
					assertArrayEquals(m.tri, f.tri, "no cut surface on " + variant);
					assertArrayEquals(m.uv, f.uv);
					assertArrayEquals(m.weight, f.weight, 1e-6F);
					int changed = 0;
					float highest = 0;
					for (int v = 0; v < m.vertexCount; v++) {
						assertEquals(m.pos[v * 3], f.pos[v * 3]);
						assertEquals(m.pos[v * 3 + 2], f.pos[v * 3 + 2]);
						float y = m.pos[v * 3 + 1], fy = f.pos[v * 3 + 1];
						if (y < mesh.height * 0.90F) assertEquals(y, fy, "head/body/feet unchanged");
						if (y != fy) changed++;
						assertTrue(fy <= y);
						highest = Math.max(highest, fy);
						float nx = f.normal[v * 3], ny = f.normal[v * 3 + 1], nz = f.normal[v * 3 + 2];
						assertEquals(1, Math.sqrt(nx * nx + ny * ny + nz * nz), 0.001);
					}
					assertTrue(changed > 0, variant + " has a short female tuft");
					assertTrue(highest < mesh.height * 0.96F && highest > mesh.height * 0.91F);
				}
			}
			// Export runtime geometry for Blender QA, without substituting an older .blend.
			if (System.getenv("CR_MODEL_PREVIEWS") != null) {
				for (boolean male : new boolean[]{true, false}) {
					for (String clip : new String[]{"idle", "run"}) {
						writePreview(mesh, variant, male, clip);
					}
				}
			}
		}
	}

	private static void writePreview(WhiskerMesh mesh, String variant, boolean male, String clipName) throws Exception {
		Path dir = Path.of("build/model-polish");
		Files.createDirectories(dir);
		WhiskerMesh.Clip clip = mesh.clipByName.get(clipName);
		float[] frame = new float[clip.bones * 12];
		System.arraycopy(clip.m, 5 * frame.length, frame, 0, frame.length);
		MeshSkinner skinner = new MeshSkinner();
		skinner.composeRows(frame, clip.bones, new float[]{1,0,0,0,0,1,0,0,0,0,1,0}, new float[]{1,0,0,0,1,0,0,0,1});
		try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(dir.resolve(variant + "_" + (male ? "male" : "female") + "_" + clipName + ".obj")))) {
			int offset = 1;
			for (WhiskerMesh.Part part : mesh.parts(0, male)) {
				skinner.skin(part, new boolean[clip.bones], false);
				for (int v = 0; v < part.vertexCount; v++) {
					int p = part.posIndex[v] * 3;
					out.printf(Locale.ROOT, "v %.7f %.7f %.7f%n", skinner.pos[p], -skinner.pos[p + 2], skinner.pos[p + 1]);
					out.printf(Locale.ROOT, "vt %.7f %.7f%n", part.uv[v * 2], 1 - part.uv[v * 2 + 1]);
				}
				for (int t = 0; t < part.tri.length; t += 3) {
					int a = offset + part.tri[t], b = offset + part.tri[t + 1], c = offset + part.tri[t + 2];
					out.printf("f %d/%d %d/%d %d/%d%n", a,a,b,b,c,c);
				}
				offset += part.vertexCount;
			}
		}
	}
}
