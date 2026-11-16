{
  description = "expr-eval JVM and JavaScript development shell";
  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";

  outputs = { nixpkgs, ... }:
    {
      devShells = nixpkgs.lib.genAttrs [
        "x86_64-linux" "aarch64-linux" "x86_64-darwin" "aarch64-darwin"
      ] (system:
        let pkgs = import nixpkgs { inherit system; };
        in {
          default = pkgs.mkShell {
            packages = [ pkgs.jdk17 pkgs.nodejs_26 pkgs.gnupg ];
            JAVA_HOME = pkgs.jdk17.home;
          };
        });
    };
}
