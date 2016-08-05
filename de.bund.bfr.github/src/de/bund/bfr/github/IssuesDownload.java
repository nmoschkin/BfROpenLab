/*******************************************************************************
 * Copyright (c) 2016 German Federal Institute for Risk Assessment (BfR)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Department Biological Safety - BfR
 *******************************************************************************/
package de.bund.bfr.github;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;

public class IssuesDownload {

	public static void main(String[] args) throws IOException {
		System.setProperty("http.proxyHost", "webproxy");
		System.setProperty("http.proxyPort", "8080");
		System.setProperty("https.proxyHost", "webproxy");
		System.setProperty("https.proxyPort", "8080");

		IssuesDownload.saveIssues("SiLeBAT/BfROpenLab");
	}

	public static void saveIssues(String repoDetails) throws IOException {
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
		System.out.println("user:");
		String user = br.readLine();
		System.out.println("password:");
		String password = br.readLine();
		br.close();

		String[] repoInfo = repoDetails.split("/");
		GitHub github = GitHub.connectUsingPassword(user, password);
		GHRepository repository = github.getUser(repoInfo[0]).getRepository(repoInfo[1]);
		List<String> columns = Arrays.asList("Id", "Title", "Creator", "Assignee", "Milestone", "Label", "Created",
				"Closed", "State");

		try (BufferedWriter writer = Files.newBufferedWriter(new File("issues.csv").toPath(), StandardCharsets.UTF_8)) {
			int index = 1;

			writer.append(Joiner.on("\t").join(columns) + "\n");

			for (GHIssue issue : Iterables.concat(repository.listIssues(GHIssueState.OPEN),
					repository.listIssues(GHIssueState.CLOSED))) {
				List<String> parts = new ArrayList<>();

				parts.add(String.valueOf(issue.getNumber()));
				parts.add(issue.getTitle());
				parts.add(issue.getUser().getLogin());

				if (issue.getAssignee() != null) {
					parts.add(issue.getAssignee().getLogin());
				} else {
					parts.add("");
				}

				if (issue.getMilestone() != null) {
					parts.add(issue.getMilestone().getTitle());
				} else {
					parts.add("");
				}

				List<String> labels = new ArrayList<>();

				for (GHLabel label : issue.getLabels()) {
					labels.add(label.getName());
				}

				parts.add(Joiner.on("+").join(labels));

				if (issue.getCreatedAt() != null) {
					parts.add(new SimpleDateFormat("yyyy-MM-dd").format(issue.getCreatedAt()));
				} else {
					parts.add("");
				}

				if (issue.getClosedAt() != null) {
					parts.add(new SimpleDateFormat("yyyy-MM-dd").format(issue.getClosedAt()));
				} else {
					parts.add("");
				}

				parts.add(issue.getState().toString());

				List<String> cleanedParts = new ArrayList<>();

				for (String s : parts) {
					cleanedParts.add(s.replace("\t", " ").replace("\n", " "));
				}

				writer.write(Joiner.on("\t").join(cleanedParts) + "\n");
				System.out.println(index++ + "\tissues processed");
			}
		}
	}
}