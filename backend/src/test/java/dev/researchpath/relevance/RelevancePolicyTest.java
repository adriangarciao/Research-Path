package dev.researchpath.relevance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.researchpath.publication.NormalizedWork;
import dev.researchpath.publication.NormalizedWork.ConceptAssociation;
import dev.researchpath.publication.NormalizedWork.ConceptKind;
import dev.researchpath.relevance.RelevancePolicy.Decision;
import dev.researchpath.relevance.RelevancePolicy.Query;

class RelevancePolicyTest {

	private static NormalizedWork work(String title, String abstractText, ConceptAssociation... concepts) {
		return new NormalizedWork("W1", null, title, title.toLowerCase(), 2020, null, "article", "en", null, null,
				null, null, abstractText, null, null, null, null, null, List.of(), List.of(concepts), List.of(), 0,
				false, true);
	}

	private static ConceptAssociation keyword(String name, Double score) {
		return new ConceptAssociation(ConceptKind.KEYWORD, "keywords/" + name.replace(' ', '-'), name, score, false,
				null, null, null);
	}

	private static ConceptAssociation topic(String name, Double score) {
		return new ConceptAssociation(ConceptKind.TOPIC, "T" + Math.abs(name.hashCode()), name, score, false, null,
				null, null);
	}

	private static Decision decide(String query, NormalizedWork w) {
		return RelevancePolicy.decide(RelevancePolicy.analyzeQuery(query), w);
	}

	@Test
	void queryAnalysisDropsStopwordsStemsAndKeepsAcronymConcepts() {
		Query q = RelevancePolicy.analyzeQuery("AI-assisted medical diagnosis");
		assertThat(q.terms()).extracting(RelevancePolicy.QueryTerm::label)
				.containsExactly("ai", "assist", "medical", "diagnosi");
		assertThat(RelevancePolicy.analyzeQuery("a survey of the graph neural networks").terms())
				.extracting(RelevancePolicy.QueryTerm::label).containsExactly("graph", "neural", "network");
		assertThat(RelevancePolicy.analyzeQuery("the of and").hasContent()).isFalse();
	}

	@Test
	void stemmingAndPrefixMatchingCoverCommonVariants() {
		assertThat(RelevancePolicy.stem("robotics")).isEqualTo("robot");
		assertThat(RelevancePolicy.stem("robotic")).isEqualTo("robot");
		assertThat(RelevancePolicy.stem("robots")).isEqualTo("robot");
		assertThat(RelevancePolicy.stem("learning")).isEqualTo("learn");
		assertThat(RelevancePolicy.stem("networks")).isEqualTo("network");
		assertThat(RelevancePolicy.stemMatch(RelevancePolicy.stem("diagnosis"), RelevancePolicy.stem("diagnostic")))
				.isTrue();
		assertThat(RelevancePolicy.stemMatch("medical", "medicine")).isFalse();
	}

	@Test
	void allTermsInTitleIsEligibleEvenWithoutAbstract() {
		Decision d = decide("reinforcement learning in robotics",
				work("Reinforcement Learning on Robot with Variational Auto-Encoder", null));
		assertThat(d.tier()).isEqualTo(RelevanceTier.ELIGIBLE);
		assertThat(d.evidence()).containsEntry("abstractAvailable", false);
	}

	@Test
	void acronymsCountAsTheirFullPhrase() {
		assertThat(decide("graph neural networks", work("KerGNNs: interpretable GNN with graph kernels", null)).tier())
				.isEqualTo(RelevanceTier.ELIGIBLE);
		assertThat(decide("AI-assisted medical diagnosis", work("Artificial Neural Networks in Medical Diagnosis", null))
				.tier()).isEqualTo(RelevanceTier.ELIGIBLE); // "ai" satisfied by neural networks; 3/4 terms
	}

	@Test
	void confidentKeywordsCountButLowOrUnknownScoresDoNot() {
		NormalizedWork base = work("Predict then Propagate with Personalized PageRank", null,
				keyword("graph neural networks", 0.9));
		assertThat(decide("graph neural networks", base).tier()).isEqualTo(RelevanceTier.ELIGIBLE);

		NormalizedWork weak = work("Predict then Propagate with Personalized PageRank", null,
				keyword("graph neural networks", 0.1), topic("Graph Neural Networks", null));
		assertThat(decide("graph neural networks", weak).tier()).isEqualTo(RelevanceTier.INELIGIBLE);
	}

	@Test
	void abstractOnlyEvidenceIsBorderlineNotEligible() {
		Decision d = decide("graph neural networks", work("Learning on structured data",
				"We study graph neural networks for molecules."));
		assertThat(d.tier()).isEqualTo(RelevanceTier.BORDERLINE);
		assertThat(d.tier().eligible()).isFalse();
	}

	@Test
	void noEvidenceIsIneligible() {
		Decision d = decide("graph neural networks", work("Quantum error correction codes", "Stabilizer codes."));
		assertThat(d.tier()).isEqualTo(RelevanceTier.INELIGIBLE);
		assertThat(d.evidence().get("missing")).isEqualTo(List.of("graph", "neural", "network"));
	}

	@Test
	void unrequestedAspectsAreTangential() {
		String q = "AI-assisted medical diagnosis";
		assertThat(decide(q, work("The Role for Policy in AI-Assisted Medical Diagnosis", null)).tier())
				.isEqualTo(RelevanceTier.TANGENTIAL);
		assertThat(decide(q, work("Medical students' perceptions of an AI assisted diagnosing program", null)).tier())
				.isEqualTo(RelevanceTier.TANGENTIAL);
		assertThat(decide(q, work("Information Security and AI-Assisted Diagnosis in an IoMT System", null)).tier())
				.isEqualTo(RelevanceTier.TANGENTIAL);
		assertThat(decide(q, work("Liability Sharing and Staffing in AI-Assisted Online Medical Consultation", null))
				.reason()).isEqualTo("ASPECT_NOT_REQUESTED");
	}

	@Test
	void requestedAspectsAreNotTangential() {
		assertThat(decide("AI policy in medical diagnosis",
				work("The Role for Policy in AI-Assisted Medical Diagnosis", null)).tier())
				.isEqualTo(RelevanceTier.ELIGIBLE);
	}

	@Test
	void technicalSensesOfCueWordsAreNotTangential() {
		String q = "reinforcement learning in robotics";
		for (String title : List.of("Safe Policy Optimization for Reinforcement Learning in Robotics",
				"Off-Policy Reinforcement Learning for Robotics",
				"Direct Policy Search Reinforcement Learning for Robot Control",
				"Teaching and reinforcement learning of robotic view-based manipulation")) {
			assertThat(decide(q, work(title, null)).tier()).as(title).isEqualTo(RelevanceTier.ELIGIBLE);
		}
		assertThat(decide("graph neural networks",
				work("Teacher-student distillation for graph neural networks", null)).tier())
				.isEqualTo(RelevanceTier.ELIGIBLE);
		// Technical security/robustness and trustworthiness work is not a cue.
		assertThat(decide("AI-assisted medical diagnosis", work(
				"Robustness in deep learning models for medical diagnostics: security and adversarial challenges", null))
				.tier()).isEqualTo(RelevanceTier.ELIGIBLE);
	}

	@Test
	void technicalUsesOfLawAreNotAspects() {
		assertThat(decide("graph neural networks", work("Graph neural networks for power-law degree graphs", null))
				.tier()).isEqualTo(RelevanceTier.ELIGIBLE);
		assertThat(decide("AI-assisted medical diagnosis",
				work("LEGAL ASPECTS OF THE USE OF ARTIFICIAL INTELLIGENCE IN TELEMEDICINE-BASED DIAGNOSIS", null))
				.tier()).isEqualTo(RelevanceTier.TANGENTIAL);
	}

	@Test
	void decisionsAreDeterministic() {
		NormalizedWork w = work("Deep Reinforcement Learning for Robotic Pushing", "abstract text");
		List<Decision> runs = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			runs.add(decide("reinforcement learning in robotics", w));
		}
		assertThat(runs).allMatch(d -> d.equals(runs.get(0)));
	}

}
