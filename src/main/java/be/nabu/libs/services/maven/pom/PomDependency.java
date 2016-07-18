package be.nabu.libs.services.maven.pom;

public class PomDependency extends PomArtifact {
	
	private PomExclusions exclusions;
	private String scope;

	public String getScope() {
		return scope;
	}

	public void setScope(String scope) {
		this.scope = scope;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((scope == null) ? 0 : scope.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		PomDependency other = (PomDependency) obj;
		if (scope == null) {
			if (other.scope != null)
				return false;
		}
		else if (!scope.equals(other.scope))
			return false;
		return true;
	}

	public PomExclusions getExclusions() {
		return exclusions;
	}
	public void setExclusions(PomExclusions exclusions) {
		this.exclusions = exclusions;
	}
}
